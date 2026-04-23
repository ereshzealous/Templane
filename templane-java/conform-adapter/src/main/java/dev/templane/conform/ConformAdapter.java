package dev.templane.conform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.templane.core.IRGenerator;
import dev.templane.core.JsonMapper;
import dev.templane.core.SchemaParser;
import dev.templane.core.TypeChecker;
import dev.templane.core.model.ASTNode;
import dev.templane.core.model.TIRResult;
import dev.templane.core.model.TypeCheckError;
import dev.templane.core.model.TypedSchema;
import dev.templane.html.HtmlAdapter;
import dev.templane.yaml.YamlAdapter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class ConformAdapter {

    private static final ObjectMapper M = JsonMapper.INSTANCE;
    private static final SchemaParser SCHEMA_PARSER = new SchemaParser();

    public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            JsonNode req = M.readTree(line);
            String fixtureId = req.get("fixture_id").asText();
            JsonNode fixture = req.get("fixture");
            ObjectNode response = M.createObjectNode();
            try {
                JsonNode output = handle(fixtureId, fixture);
                response.set("output", output);
            } catch (Exception e) {
                response.putNull("output");
                response.put("error", e.getMessage());
            }
            System.out.println(M.writeValueAsString(response));
            System.out.flush();
        }
    }

    static JsonNode handle(String fixtureId, JsonNode fixture) throws Exception {
        if (fixtureId.startsWith("schema-parser")) {
            String yaml = fixture.get("yaml").asText();
            String id = fixture.has("id") ? fixture.get("id").asText() : "unknown";
            SchemaParser.Result r = SCHEMA_PARSER.parse(yaml, id);
            ObjectNode out = M.createObjectNode();
            if (r.error() != null) {
                out.put("error", r.error());
            } else {
                out.set("schema", M.valueToTree(r.schema()));
                if (r.body() != null) out.put("body", r.body());
            }
            return out;
        }

        if (fixtureId.startsWith("type-checker")) {
            TypedSchema schema = M.treeToValue(fixture.get("schema"), TypedSchema.class);
            Map<String, Object> data = M.convertValue(fixture.get("data"), new TypeReference<>() {});
            List<TypeCheckError> errors = TypeChecker.check(schema, data);
            ObjectNode out = M.createObjectNode();
            out.set("errors", M.valueToTree(errors));
            return out;
        }

        if (fixtureId.startsWith("ir-generator")) {
            List<ASTNode> ast = M.convertValue(fixture.get("ast"), new TypeReference<>() {});
            Map<String, Object> data = M.convertValue(fixture.get("data"), new TypeReference<>() {});
            String schemaId = fixture.get("schema_id").asText();
            String templateId = fixture.get("template_id").asText();
            TIRResult result = IRGenerator.generate(ast, data, schemaId, templateId);
            return M.valueToTree(result);
        }

        if (fixtureId.startsWith("adapters/html")) {
            TIRResult tir = M.treeToValue(fixture.get("tir"), TIRResult.class);
            ObjectNode out = M.createObjectNode();
            out.put("output", HtmlAdapter.render(tir));
            return out;
        }

        if (fixtureId.startsWith("adapters/yaml")) {
            TIRResult tir = M.treeToValue(fixture.get("tir"), TIRResult.class);
            ObjectNode out = M.createObjectNode();
            out.put("output", YamlAdapter.render(tir));
            return out;
        }

        ObjectNode unhandled = M.createObjectNode();
        unhandled.putNull("output");
        unhandled.put("error", "Unhandled fixture: " + fixtureId);
        return unhandled;
    }
}
