package dev.tsp.conform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.tsp.core.JsonMapper;
import dev.tsp.core.SchemaParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

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
        ObjectNode unhandled = M.createObjectNode();
        unhandled.putNull("output");
        unhandled.put("error", "Unhandled fixture: " + fixtureId);
        return unhandled;
    }
}
