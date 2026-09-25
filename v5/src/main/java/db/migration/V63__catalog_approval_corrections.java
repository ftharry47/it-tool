package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;

/**
 * Approval corrections: licensed spend and hardware purchases require
 * approval (ServiceNow convention). Approval is PER-OPTION — V53 dropped
 * catalog_item.approval_required and moved the flag into form_schema
 * options (requiresApproval / otherRequiresApproval), so this migration
 * sets requiresApproval=true on every option of every select field for
 * the named items. Name-keyed, idempotent: it only ever sets true and
 * touches only the three named rows.
 */
public class V63__catalog_approval_corrections extends BaseJavaMigration {

    private static final Set<String> ITEMS = Set.of(
            "Clinical Software",
            "Clinical Workstation / Device",
            "Mobile Device / BYOD");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        try (Statement select = connection.createStatement();
             ResultSet rs = select.executeQuery(
                     "SELECT id, name, form_schema FROM catalog_item WHERE deleted_at IS NULL")) {

            while (rs.next()) {
                String name = rs.getString("name");
                if (!ITEMS.contains(name)) {
                    continue;
                }

                UUID id = UUID.fromString(rs.getString("id"));
                JsonNode schema = objectMapper.readTree(rs.getString("form_schema"));
                if (!schema.isArray()) {
                    continue;
                }

                boolean changed = false;
                for (JsonNode fieldNode : schema) {
                    if (!fieldNode.isObject()) {
                        continue;
                    }
                    ObjectNode field = (ObjectNode) fieldNode;
                    String type = field.has("type") ? field.get("type").asText() : "string";
                    if (!"select".equals(type) && !"select_with_other".equals(type)) {
                        continue;
                    }

                    JsonNode options = field.get("options");
                    if (options != null && options.isArray()) {
                        com.fasterxml.jackson.databind.node.ArrayNode arr =
                                (com.fasterxml.jackson.databind.node.ArrayNode) options;
                        for (int i = 0; i < arr.size(); i++) {
                            JsonNode opt = arr.get(i);
                            if (opt.isTextual()) {
                                // V53-shaped object so the option carries its
                                // own approval flag (value + label preserved).
                                ObjectNode obj = objectMapper.createObjectNode();
                                obj.put("value", opt.asText());
                                obj.put("label", opt.asText());
                                obj.put("requiresApproval", true);
                                arr.set(i, obj);
                                changed = true;
                            } else if (opt.isObject()
                                    && !(opt.hasNonNull("requiresApproval")
                                            && opt.get("requiresApproval").asBoolean())) {
                                ((ObjectNode) opt).put("requiresApproval", true);
                                changed = true;
                            }
                        }
                    }
                    if ("select_with_other".equals(type)
                            && !(field.hasNonNull("otherRequiresApproval")
                                    && field.get("otherRequiresApproval").asBoolean())) {
                        field.put("otherRequiresApproval", true);
                        changed = true;
                    }
                }

                if (!changed) {
                    continue;
                }

                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE catalog_item SET form_schema = ?::jsonb, updated_at = now() WHERE id = ?")) {
                    update.setString(1, objectMapper.writeValueAsString(schema));
                    update.setObject(2, id);
                    update.executeUpdate();
                }
            }
        }
    }
}
