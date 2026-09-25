package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Approval corrections: licensed spend and hardware purchases require
 * approval (ServiceNow convention). Approval is PER-OPTION — V53 dropped
 * catalog_item.approval_required and moved the flag into form_schema
 * options (requiresApproval / otherRequiresApproval), so this migration
 * sets requiresApproval=true on every option of every select field for
 * the named items (post-V52 catalog names). Only-ever-sets-true, and each
 * target name's match count is logged — a name matching zero rows is a
 * loud warning, never a silent no-op.
 */
public class V63__catalog_approval_corrections extends BaseJavaMigration {

    private static final Logger logger = LoggerFactory.getLogger(V63__catalog_approval_corrections.class);

    private static final List<String> ITEMS = List.of(
            "New / Replacement Clinical Software",
            "New Clinical Workstation / Device",
            "New Mobile Device / BYOD Request");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        Map<String, Integer> matched = new LinkedHashMap<>();
        ITEMS.forEach(name -> matched.put(name, 0));
        int updated = 0;

        try (Statement select = connection.createStatement();
             ResultSet rs = select.executeQuery(
                     "SELECT id, name, form_schema FROM catalog_item WHERE deleted_at IS NULL")) {

            while (rs.next()) {
                String name = rs.getString("name");
                if (!ITEMS.contains(name)) {
                    continue;
                }
                matched.merge(name, 1, Integer::sum);

                UUID id = UUID.fromString(rs.getString("id"));
                String schemaJson = rs.getString("form_schema");
                JsonNode schema = schemaJson != null ? objectMapper.readTree(schemaJson) : null;
                if (schema == null || !schema.isArray()) {
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
                        ArrayNode arr = (ArrayNode) options;
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
                    updated++;
                }
            }
        }

        matched.forEach((name, count) -> {
            if (count == 0) {
                logger.error("V63: catalog item '{}' matched ZERO rows — "
                        + "the name may have been renamed again; approval was NOT set for it", name);
            } else {
                logger.info("V63: catalog item '{}' matched {} row(s)", name, count);
            }
        });
        logger.info("V63: {} catalog item(s) updated with requiresApproval=true", updated);
        if (matched.values().stream().anyMatch(c -> c == 0)) {
            throw new IllegalStateException(
                    "V63 matched no rows for at least one expected catalog item — "
                            + "check the log for which name(s) missed");
        }
    }
}
