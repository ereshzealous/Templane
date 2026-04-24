package core

import (
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"

	"gopkg.in/yaml.v3"
)

// ParseResult is the outcome of parsing a Templane schema document.
// On success, Schema is populated; optional fields describe body resolution.
// On failure, Error is populated.
type ParseResult struct {
	Schema   *TypedSchema `json:"schema,omitempty"`
	Body     *string      `json:"body,omitempty"`      // nil if no embedded body / unresolved sidecar
	BodyPath *string      `json:"body_path,omitempty"` // nil when embedded or check-only
	Engine   *string      `json:"engine,omitempty"`    // nil when unknown
	Error    string       `json:"error,omitempty"`
}

// Reserved top-level keys per SPEC §4.3 — not treated as field names.
var reservedSchemaKeys = map[string]struct{}{
	"body":   {},
	"engine": {},
}

// Valid engine identifiers per SPEC §4.3.
var validEngines = map[string]struct{}{
	"jinja":      {},
	"handlebars": {},
	"freemarker": {},
	"gotemplate": {},
	"markdown":   {},
	"html-raw":   {},
	"yaml-raw":   {},
}

// engineByExt maps lowercase file extensions to engine identifiers.
var engineByExt = map[string]string{
	".jinja":      "jinja",
	".jinja2":     "jinja",
	".j2":         "jinja",
	".hbs":        "handlebars",
	".handlebars": "handlebars",
	".ftl":        "freemarker",
	".ftlh":       "freemarker",
	".tmpl":       "gotemplate",
	".gotmpl":     "gotemplate",
	".md":         "markdown",
	".markdown":   "markdown",
	".html":       "html-raw",
	".htm":        "html-raw",
	".yaml":       "yaml-raw",
	".yml":        "yaml-raw",
}

// ParseSchema parses a YAML schema document.
//
// Two modes are supported:
//   - Embedded: YAML + "\n---\n" + body (body is inlined in the same string).
//   - Sidecar:  YAML contains a reserved top-level "body:" key pointing to an external file.
//
// Sidecar mode only sets BodyPath and Engine; the body itself is not loaded here.
// See LoadSchemaFromPath for full sidecar resolution.
func ParseSchema(yamlStr, schemaID string) ParseResult {
	var body *string
	schemaYAML := yamlStr
	hasSeparator := false
	if idx := strings.Index(yamlStr, "\n---\n"); idx >= 0 {
		schemaYAML = yamlStr[:idx]
		b := yamlStr[idx+5:]
		body = &b
		hasSeparator = true
	}

	var data any
	if err := yaml.Unmarshal([]byte(schemaYAML), &data); err != nil {
		return ParseResult{Error: err.Error()}
	}

	mapData, ok := data.(map[string]any)
	if !ok {
		return ParseResult{Error: "Schema must be a YAML mapping"}
	}

	// Extract reserved keys up-front so they are never treated as fields.
	var bodyPath *string
	if raw, present := mapData["body"]; present {
		if s, isStr := raw.(string); isStr {
			bodyPath = &s
		}
		delete(mapData, "body")
	}
	var engine *string
	if raw, present := mapData["engine"]; present {
		if s, isStr := raw.(string); isStr {
			engine = &s
		}
		delete(mapData, "engine")
	}

	if bodyPath != nil && hasSeparator {
		return ParseResult{Error: "cannot use both 'body:' key and '---' separator"}
	}

	if bodyPath != nil {
		if err := validateBodyPath(*bodyPath); err != "" {
			return ParseResult{Error: err}
		}
	}

	if engine != nil {
		if _, ok := validEngines[*engine]; !ok {
			return ParseResult{Error: fmt.Sprintf("unknown engine '%s' — must be one of %v", *engine, sortedEngineList())}
		}
	}

	// Infer engine from body-path extension when not explicit.
	if engine == nil && bodyPath != nil {
		ext := strings.ToLower(filepath.Ext(*bodyPath))
		if inferred, ok := engineByExt[ext]; ok {
			e := inferred
			engine = &e
		}
	}

	// Build field set from remaining top-level keys.
	fields := make(map[string]TemplaneField, len(mapData))
	for name, fieldDef := range mapData {
		if _, reserved := reservedSchemaKeys[name]; reserved {
			continue // defensive; reserved keys were already removed
		}
		def, _ := fieldDef.(map[string]any)
		fields[name] = parseField(name, def)
	}
	schema := &TypedSchema{ID: schemaID, Fields: fields}
	return ParseResult{Schema: schema, Body: body, BodyPath: bodyPath, Engine: engine}
}

// LoadSchemaFromPath reads a schema file from disk and resolves an external
// body file if the schema is in sidecar mode. Returns ParseResult.Error on any
// read or validation failure.
func LoadSchemaFromPath(schemaPath string) ParseResult {
	data, err := os.ReadFile(schemaPath)
	if err != nil {
		return ParseResult{Error: fmt.Sprintf("cannot read schema file: %s", err.Error())}
	}
	result := ParseSchema(string(data), filepath.Base(schemaPath))
	if result.Error != "" {
		return result
	}
	if result.BodyPath != nil && result.Body == nil {
		bodyAbs := filepath.Join(filepath.Dir(schemaPath), *result.BodyPath)
		bodyData, err := os.ReadFile(bodyAbs)
		if err != nil {
			return ParseResult{Error: fmt.Sprintf("body file not found: %s", *result.BodyPath)}
		}
		b := string(bodyData)
		result.Body = &b
	}
	return result
}

// validateBodyPath returns an error message if the body path is absolute or
// contains a ".." path component, and empty string otherwise.
func validateBodyPath(p string) string {
	if filepath.IsAbs(p) || strings.HasPrefix(p, "/") {
		return "body path must be relative and inside the schema's directory"
	}
	// Check for ".." path segments (normalize separators for cross-platform safety).
	normalized := strings.ReplaceAll(p, "\\", "/")
	for _, seg := range strings.Split(normalized, "/") {
		if seg == ".." {
			return "body path must be relative and inside the schema's directory"
		}
	}
	return ""
}

func sortedEngineList() []string {
	out := make([]string, 0, len(validEngines))
	for k := range validEngines {
		out = append(out, k)
	}
	sort.Strings(out)
	return out
}

func parseField(name string, def map[string]any) TemplaneField {
	typeStr := "string"
	if t, ok := def["type"].(string); ok {
		typeStr = t
	}
	required, _ := def["required"].(bool)
	return TemplaneField{Name: name, Type: parseType(typeStr, def), Required: required}
}

func parseType(typeStr string, def map[string]any) TemplaneFieldType {
	switch typeStr {
	case "string":
		return TemplaneFieldType{Kind: "string"}
	case "number":
		return TemplaneFieldType{Kind: "number"}
	case "boolean":
		return TemplaneFieldType{Kind: "boolean"}
	case "null":
		return TemplaneFieldType{Kind: "null"}
	case "enum":
		raw, _ := def["values"].([]any)
		values := make([]string, 0, len(raw))
		for _, v := range raw {
			values = append(values, toString(v))
		}
		return TemplaneFieldType{Kind: "enum", Values: values}
	case "list":
		items, _ := def["items"].(map[string]any)
		itemTypeStr := "string"
		if s, ok := items["type"].(string); ok {
			itemTypeStr = s
		}
		it := parseType(itemTypeStr, items)
		return TemplaneFieldType{Kind: "list", ItemType: &it}
	case "object":
		sub := make(map[string]TemplaneField)
		subDefs, _ := def["fields"].(map[string]any)
		for fname, fdef := range subDefs {
			fd, _ := fdef.(map[string]any)
			sub[fname] = parseField(fname, fd)
		}
		return TemplaneFieldType{Kind: "object", Fields: sub}
	}
	return TemplaneFieldType{Kind: "string"}
}

func toString(v any) string {
	if s, ok := v.(string); ok {
		return s
	}
	// Fall back to %v formatting for numbers/booleans.
	switch x := v.(type) {
	case bool:
		if x {
			return "true"
		}
		return "false"
	case int:
		return intToString(x)
	case int64:
		return intToString(int(x))
	case float64:
		return floatToString(x)
	}
	return ""
}

func intToString(i int) string {
	if i == 0 {
		return "0"
	}
	neg := i < 0
	if neg {
		i = -i
	}
	var buf [20]byte
	pos := len(buf)
	for i > 0 {
		pos--
		buf[pos] = byte('0' + i%10)
		i /= 10
	}
	if neg {
		pos--
		buf[pos] = '-'
	}
	return string(buf[pos:])
}

func floatToString(f float64) string {
	// Minimal: cast to int if whole number.
	if f == float64(int(f)) {
		return intToString(int(f))
	}
	// Let fmt handle the rare non-integer case at call sites.
	return ""
}
