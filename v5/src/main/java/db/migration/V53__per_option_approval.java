package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

public class V53__per_option_approval extends BaseJavaMigration {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();

        try (Statement select = connection.createStatement();
             ResultSet rs = select.executeQuery(
                     "SELECT id, form_schema, approval_required FROM catalog_item")) {

            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("id"));
                String schemaJson = rs.getString("form_schema");
                boolean oldApprovalRequired = rs.getBoolean("approval_required");

                JsonNode schema = objectMapper.readTree(schemaJson);
                if (!schema.isArray()) {
                    continue;
                }

                for (JsonNode fieldNode : schema) {
                    if (!fieldNode.isObject()) {
                        continue;
                    }
                    ObjectNode field = (ObjectNode) fieldNode;
                    String type = field.has("type") ? field.get("type").asText() : "string";
                    if (!("select".equals(type) || "select_with_other".equals(type))) {
                        continue;
                    }

                    JsonNode optionsNode = field.get("options");
                    if (optionsNode == null || !optionsNode.isArray()) {
                        continue;
                    }

                    ArrayNode newOptions = objectMapper.createArrayNode();
                    for (JsonNode optNode : optionsNode) {
                        if (optNode.isTextual()) {
                            ObjectNode obj = objectMapper.createObjectNode();
                            String text = optNode.asText();
                            obj.put("value", text);
                            obj.put("label", text);
                            obj.put("requiresApproval", oldApprovalRequired);
                            newOptions.add(obj);
                        } else if (optNode.isObject()) {
                            ObjectNode obj = (ObjectNode) optNode.deepCopy();
                            if (!obj.has("value") || obj.get("value").isNull()) {
                                // Defensive: skip malformed option objects.
                                continue;
                            }
                            if (!obj.has("label") || obj.get("label").isNull()) {
                                obj.put("label", obj.get("value").asText());
                            }
                            if (!obj.has("requiresApproval")) {
                                obj.put("requiresApproval", oldApprovalRequired);
                            }
                            newOptions.add(obj);
                        }
                    }

                    field.set("options", newOptions);

                    if ("select_with_other".equals(type)) {
                        field.put("otherRequiresApproval", oldApprovalRequired);
                    }
                }

                String newSchema = objectMapper.writeValueAsString(schema);

                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE catalog_item SET form_schema = ?::jsonb WHERE id = ?")) {
                    update.setString(1, newSchema);
                    update.setObject(2, id);
                    update.executeUpdate();
                }
            }
        }

        try (Statement drop = connection.createStatement()) {
            drop.execute("ALTER TABLE catalog_item DROP COLUMN approval_required");
        }
    }
}
