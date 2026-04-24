package core

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// Tests for SPEC §4.3 — sidecar mode.

func TestSidecarBodyPathRecognized(t *testing.T) {
	yaml := "body: ./email.jinja\nname:\n  type: string\n  required: true\n"
	r := ParseSchema(yaml, "sidecar-basic")
	if r.Error != "" {
		t.Fatalf("unexpected error: %s", r.Error)
	}
	if r.BodyPath == nil || *r.BodyPath != "./email.jinja" {
		t.Fatalf("body_path = %v, want ./email.jinja", r.BodyPath)
	}
	// body: key must NOT be treated as a field
	if _, ok := r.Schema.Fields["body"]; ok {
		t.Errorf("'body' must not be a field")
	}
	if len(r.Schema.Fields) != 1 {
		t.Errorf("expected 1 field, got %d", len(r.Schema.Fields))
	}
	if _, ok := r.Schema.Fields["name"]; !ok {
		t.Errorf("missing 'name' field")
	}
}

func TestSidecarEngineExplicit(t *testing.T) {
	yaml := "body: ./t.hbs\nengine: jinja\nuser: {type: string, required: true}\n"
	r := ParseSchema(yaml, "engine-explicit")
	if r.Error != "" {
		t.Fatalf("unexpected error: %s", r.Error)
	}
	// explicit engine wins even when extension disagrees
	if r.Engine == nil || *r.Engine != "jinja" {
		t.Errorf("engine = %v, want jinja", r.Engine)
	}
}

func TestSidecarEngineInferredFromExtension(t *testing.T) {
	cases := []struct {
		bodyPath string
		engine   string
	}{
		{"./t.jinja", "jinja"},
		{"./t.hbs", "handlebars"},
		{"./t.ftl", "freemarker"},
		{"./t.tmpl", "gotemplate"},
		{"./t.md", "markdown"},
		{"./t.html", "html-raw"},
	}
	for _, c := range cases {
		yaml := "body: " + c.bodyPath + "\nname: {type: string, required: true}\n"
		r := ParseSchema(yaml, "inferred")
		if r.Error != "" {
			t.Errorf("for %s: unexpected error %q", c.bodyPath, r.Error)
			continue
		}
		if r.Engine == nil || *r.Engine != c.engine {
			t.Errorf("for %s: engine = %v, want %s", c.bodyPath, r.Engine, c.engine)
		}
	}
}

func TestSidecarNoExtensionNoEngineInference(t *testing.T) {
	yaml := "body: ./t\nname: {type: string, required: true}\n"
	r := ParseSchema(yaml, "no-ext")
	if r.Error != "" {
		t.Fatalf("unexpected error: %s", r.Error)
	}
	// no extension → no inference (engine optional, no error)
	if r.Engine != nil {
		t.Errorf("engine = %v, want nil", *r.Engine)
	}
}

func TestSidecarUnknownEngineRejected(t *testing.T) {
	yaml := "body: ./t.jinja\nengine: mystery\nname: {type: string, required: true}\n"
	r := ParseSchema(yaml, "bad-engine")
	if r.Error == "" {
		t.Fatalf("expected error, got none")
	}
	if !strings.Contains(r.Error, "mystery") {
		t.Errorf("error %q does not mention 'mystery'", r.Error)
	}
}

func TestSidecarAbsolutePathRejected(t *testing.T) {
	yaml := "body: /etc/passwd\nname: {type: string, required: true}\n"
	r := ParseSchema(yaml, "abs-path")
	if r.Error == "" {
		t.Fatalf("expected error, got none")
	}
	if !strings.Contains(strings.ToLower(r.Error), "relative") {
		t.Errorf("error %q does not mention 'relative'", r.Error)
	}
}

func TestSidecarParentEscapeRejected(t *testing.T) {
	yaml := "body: ../../../etc/passwd\nname: {type: string, required: true}\n"
	r := ParseSchema(yaml, "escape")
	if r.Error == "" {
		t.Fatalf("expected error, got none")
	}
}

func TestSidecarAndSeparatorConflictRejected(t *testing.T) {
	yaml := "body: ./a.jinja\nname: {type: string, required: true}\n---\nHello\n"
	r := ParseSchema(yaml, "conflict")
	if r.Error == "" {
		t.Fatalf("expected error, got none")
	}
	if !strings.Contains(strings.ToLower(r.Error), "both") {
		t.Errorf("error %q does not mention 'both'", r.Error)
	}
}

func TestEmbeddedModeUnchangedBy1_1(t *testing.T) {
	// Backward compat: a 1.0 schema with --- separator still works.
	yaml := "name:\n  type: string\n  required: true\n---\nHello {{ name }}!\n"
	r := ParseSchema(yaml, "embedded")
	if r.Error != "" {
		t.Fatalf("unexpected error: %s", r.Error)
	}
	if r.BodyPath != nil {
		t.Errorf("body_path = %v, want nil", *r.BodyPath)
	}
	if r.Engine != nil {
		t.Errorf("engine = %v, want nil", *r.Engine)
	}
	if r.Body == nil || *r.Body != "Hello {{ name }}!\n" {
		t.Errorf("body = %v", r.Body)
	}
}

func TestCheckOnlyMode(t *testing.T) {
	// Schema with neither body: key nor --- → check-only, no body emitted.
	yaml := "name:\n  type: string\n  required: true\n"
	r := ParseSchema(yaml, "check-only")
	if r.Error != "" {
		t.Fatalf("unexpected error: %s", r.Error)
	}
	if r.Body != nil {
		t.Errorf("body = %v, want nil", *r.Body)
	}
	if r.BodyPath != nil {
		t.Errorf("body_path = %v, want nil", *r.BodyPath)
	}
}

func TestLoadSchemaFromPathResolvesSidecar(t *testing.T) {
	tmp := t.TempDir()
	bodyPath := filepath.Join(tmp, "greeting.jinja")
	if err := os.WriteFile(bodyPath, []byte("Hello {{ name }}!\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	schemaPath := filepath.Join(tmp, "greeting.templane")
	schemaSrc := "body: ./greeting.jinja\nname: {type: string, required: true}\n"
	if err := os.WriteFile(schemaPath, []byte(schemaSrc), 0o644); err != nil {
		t.Fatal(err)
	}

	r := LoadSchemaFromPath(schemaPath)
	if r.Error != "" {
		t.Fatalf("unexpected error: %s", r.Error)
	}
	if r.BodyPath == nil || *r.BodyPath != "./greeting.jinja" {
		t.Errorf("body_path = %v, want ./greeting.jinja", r.BodyPath)
	}
	if r.Body == nil || *r.Body != "Hello {{ name }}!\n" {
		t.Errorf("body = %v, want 'Hello {{ name }}!\\n'", r.Body)
	}
	if r.Engine == nil || *r.Engine != "jinja" {
		t.Errorf("engine = %v, want jinja", r.Engine)
	}
}

func TestLoadSchemaFromPathMissingBodyFile(t *testing.T) {
	tmp := t.TempDir()
	schemaPath := filepath.Join(tmp, "broken.templane")
	schemaSrc := "body: ./nope.jinja\nname: {type: string, required: true}\n"
	if err := os.WriteFile(schemaPath, []byte(schemaSrc), 0o644); err != nil {
		t.Fatal(err)
	}
	r := LoadSchemaFromPath(schemaPath)
	if r.Error == "" {
		t.Fatalf("expected error, got none")
	}
	if !strings.Contains(strings.ToLower(r.Error), "body file") {
		t.Errorf("error %q does not mention 'body file'", r.Error)
	}
}

func TestLoadSchemaFromPathEmbeddedMode(t *testing.T) {
	// load_from_path on an embedded schema should work the same as parse()
	tmp := t.TempDir()
	schemaPath := filepath.Join(tmp, "embedded.templane")
	schemaSrc := "name: {type: string, required: true}\n---\nHi {{ name }}\n"
	if err := os.WriteFile(schemaPath, []byte(schemaSrc), 0o644); err != nil {
		t.Fatal(err)
	}
	r := LoadSchemaFromPath(schemaPath)
	if r.Error != "" {
		t.Fatalf("unexpected error: %s", r.Error)
	}
	if r.Body == nil || *r.Body != "Hi {{ name }}\n" {
		t.Errorf("body = %v, want 'Hi {{ name }}\\n'", r.Body)
	}
	if r.BodyPath != nil {
		t.Errorf("body_path = %v, want nil", *r.BodyPath)
	}
}
