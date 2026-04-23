package yamladapter

import (
	"strings"
	"testing"

	"tsp-go/core"
)

func TestYamlBasic(t *testing.T) {
	r := core.TIRResult{TemplateID: "greeting", SchemaID: "user", Nodes: []core.TIRNode{
		{Kind: "text", Content: "name: "},
		{Kind: "expr", Field: "name", Resolved: "Alice"},
	}}
	got := Render(r)
	want := "# tsp template_id=greeting schema_id=user\nname: Alice"
	if got != want {
		t.Errorf("got %q, want %q", got, want)
	}
}

func TestYamlDoesNotEscape(t *testing.T) {
	r := core.TIRResult{TemplateID: "t", SchemaID: "s", Nodes: []core.TIRNode{
		{Kind: "expr", Field: "content", Resolved: "<b>Hello</b>"},
	}}
	got := Render(r)
	if !strings.Contains(got, "<b>Hello</b>") {
		t.Errorf("got %q", got)
	}
}

func TestYamlProvenanceComment(t *testing.T) {
	r := core.TIRResult{TemplateID: "my-template", SchemaID: "my-schema", Nodes: []core.TIRNode{
		{Kind: "text", Content: "Hello"},
	}}
	got := Render(r)
	if !strings.HasPrefix(got, "# tsp template_id=my-template schema_id=my-schema") {
		t.Errorf("got %q", got)
	}
}
