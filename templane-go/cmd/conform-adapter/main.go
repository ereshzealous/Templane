// Command conform-adapter is the templane-go subprocess shim invoked by templane-conform.
// It reads line-delimited JSON from stdin, routes by fixture_id, and writes
// one JSON response per line to stdout.
package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"os"

	"github.com/ereshzealous/Templane/templane-go/core"
	"github.com/ereshzealous/Templane/templane-go/htmladapter"
	"github.com/ereshzealous/Templane/templane-go/yamladapter"
)

type request struct {
	FixtureID string          `json:"fixture_id"`
	Fixture   json.RawMessage `json:"fixture"`
}

type response struct {
	Output any    `json:"output"`
	Error  string `json:"error,omitempty"`
}

func main() {
	reader := bufio.NewReader(os.Stdin)
	writer := bufio.NewWriter(os.Stdout)
	defer writer.Flush()

	for {
		line, err := reader.ReadString('\n')
		if len(line) > 0 {
			processLine(line, writer)
			writer.Flush()
		}
		if err != nil {
			return
		}
	}
}

func processLine(line string, out *bufio.Writer) {
	var req request
	if err := json.Unmarshal([]byte(line), &req); err != nil {
		writeResponse(out, response{Output: nil, Error: err.Error()})
		return
	}
	output, err := handle(req.FixtureID, req.Fixture)
	if err != nil {
		writeResponse(out, response{Output: nil, Error: err.Error()})
		return
	}
	writeResponse(out, response{Output: output})
}

func writeResponse(out *bufio.Writer, resp response) {
	b, err := json.Marshal(resp)
	if err != nil {
		fmt.Fprintf(out, `{"output":null,"error":%q}`+"\n", err.Error())
		return
	}
	out.Write(b)
	out.WriteByte('\n')
}

func handle(fixtureID string, fixture json.RawMessage) (any, error) {
	switch {
	case hasPrefix(fixtureID, "schema-parser"):
		var f struct {
			Yaml string `json:"yaml"`
			ID   string `json:"id"`
		}
		if err := json.Unmarshal(fixture, &f); err != nil {
			return nil, err
		}
		id := f.ID
		if id == "" {
			id = "unknown"
		}
		r := core.ParseSchema(f.Yaml, id)
		out := map[string]any{}
		if r.Error != "" {
			out["error"] = r.Error
			return out, nil
		}
		out["schema"] = r.Schema
		if r.Body != nil {
			out["body"] = *r.Body
		}
		if r.BodyPath != nil {
			out["body_path"] = *r.BodyPath
		}
		if r.Engine != nil {
			out["engine"] = *r.Engine
		}
		return out, nil

	case hasPrefix(fixtureID, "type-checker"):
		var f struct {
			Schema *core.TypedSchema `json:"schema"`
			Data   map[string]any    `json:"data"`
		}
		if err := json.Unmarshal(fixture, &f); err != nil {
			return nil, err
		}
		errors := core.Check(f.Schema, f.Data)
		if errors == nil {
			errors = []core.TypeCheckError{}
		}
		return map[string]any{"errors": errors}, nil

	case hasPrefix(fixtureID, "ir-generator"):
		var f struct {
			AST        []core.ASTNode `json:"ast"`
			Data       map[string]any `json:"data"`
			SchemaID   string         `json:"schema_id"`
			TemplateID string         `json:"template_id"`
		}
		if err := json.Unmarshal(fixture, &f); err != nil {
			return nil, err
		}
		return core.Generate(f.AST, f.Data, f.SchemaID, f.TemplateID), nil

	case hasPrefix(fixtureID, "adapters/html"):
		var f struct {
			TIR core.TIRResult `json:"tir"`
		}
		if err := json.Unmarshal(fixture, &f); err != nil {
			return nil, err
		}
		return map[string]any{"output": htmladapter.Render(f.TIR)}, nil

	case hasPrefix(fixtureID, "adapters/yaml"):
		var f struct {
			TIR core.TIRResult `json:"tir"`
		}
		if err := json.Unmarshal(fixture, &f); err != nil {
			return nil, err
		}
		return map[string]any{"output": yamladapter.Render(f.TIR)}, nil
	}
	return map[string]any{"output": nil, "error": "Unhandled fixture: " + fixtureID}, nil
}

func hasPrefix(s, prefix string) bool {
	return len(s) >= len(prefix) && s[:len(prefix)] == prefix
}
