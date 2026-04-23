package core

import (
	"strings"

	"gopkg.in/yaml.v3"
)

// ParseResult is the outcome of parsing a Templane schema document.
// Exactly one of (Schema, Body) or Error is populated.
type ParseResult struct {
	Schema *TypedSchema
	Body   *string // nil if no body separator was present
	Error  string
}

// ParseSchema parses a YAML schema document (optionally followed by "\n---\n<body>").
func ParseSchema(yamlStr, schemaID string) ParseResult {
	var body *string
	schemaYAML := yamlStr
	if idx := strings.Index(yamlStr, "\n---\n"); idx >= 0 {
		schemaYAML = yamlStr[:idx]
		b := yamlStr[idx+5:]
		body = &b
	}

	var data any
	if err := yaml.Unmarshal([]byte(schemaYAML), &data); err != nil {
		return ParseResult{Error: err.Error()}
	}

	mapData, ok := data.(map[string]any)
	if !ok {
		return ParseResult{Error: "Schema must be a YAML mapping"}
	}

	fields := make(map[string]TemplaneField, len(mapData))
	for name, fieldDef := range mapData {
		def, _ := fieldDef.(map[string]any)
		fields[name] = parseField(name, def)
	}
	schema := &TypedSchema{ID: schemaID, Fields: fields}
	return ParseResult{Schema: schema, Body: body}
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
