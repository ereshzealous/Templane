// Package core defines the TSP data model and core operations.
package core

import "encoding/json"

// ---------------------------------------------------------------------------
// TSPFieldType — discriminated by Kind.
// ---------------------------------------------------------------------------

type TSPFieldType struct {
	Kind     string
	Values   []string
	ItemType *TSPFieldType
	Fields   map[string]TSPField
}

func (t TSPFieldType) MarshalJSON() ([]byte, error) {
	m := map[string]any{"kind": t.Kind}
	switch t.Kind {
	case "enum":
		if t.Values == nil {
			m["values"] = []string{}
		} else {
			m["values"] = t.Values
		}
	case "list":
		m["item_type"] = t.ItemType
	case "object":
		if t.Fields == nil {
			m["fields"] = map[string]TSPField{}
		} else {
			m["fields"] = t.Fields
		}
	}
	return json.Marshal(m)
}

func (t *TSPFieldType) UnmarshalJSON(data []byte) error {
	var env struct {
		Kind     string              `json:"kind"`
		Values   []string            `json:"values"`
		ItemType *TSPFieldType       `json:"item_type"`
		Fields   map[string]TSPField `json:"fields"`
	}
	if err := json.Unmarshal(data, &env); err != nil {
		return err
	}
	t.Kind = env.Kind
	t.Values = env.Values
	t.ItemType = env.ItemType
	t.Fields = env.Fields
	return nil
}

// ---------------------------------------------------------------------------

type TSPField struct {
	Name     string       `json:"name"`
	Type     TSPFieldType `json:"type"`
	Required bool         `json:"required"`
}

type TypedSchema struct {
	ID     string              `json:"id"`
	Fields map[string]TSPField `json:"fields"`
}

type TypeCheckError struct {
	Code    string `json:"code"`
	Field   string `json:"field"`
	Message string `json:"message"`
}

// ---------------------------------------------------------------------------
// AST — discriminated by Kind.
// ---------------------------------------------------------------------------

type Condition struct {
	Op    string `json:"op"`
	Left  string `json:"left"`
	Right any    `json:"right"`
}

type ASTNode struct {
	Kind       string
	Content    string
	Field      string
	Condition  *Condition
	ThenBranch []ASTNode
	ElseBranch []ASTNode
	Var        string
	Iterable   string
	Body       []ASTNode
}

func (n ASTNode) MarshalJSON() ([]byte, error) {
	switch n.Kind {
	case "text":
		return json.Marshal(struct {
			Kind    string `json:"kind"`
			Content string `json:"content"`
		}{n.Kind, n.Content})
	case "expr":
		return json.Marshal(struct {
			Kind  string `json:"kind"`
			Field string `json:"field"`
		}{n.Kind, n.Field})
	case "if":
		then := n.ThenBranch
		if then == nil {
			then = []ASTNode{}
		}
		els := n.ElseBranch
		if els == nil {
			els = []ASTNode{}
		}
		return json.Marshal(struct {
			Kind       string     `json:"kind"`
			Condition  *Condition `json:"condition"`
			ThenBranch []ASTNode  `json:"then_branch"`
			ElseBranch []ASTNode  `json:"else_branch"`
		}{n.Kind, n.Condition, then, els})
	case "foreach":
		body := n.Body
		if body == nil {
			body = []ASTNode{}
		}
		return json.Marshal(struct {
			Kind     string    `json:"kind"`
			Var      string    `json:"var"`
			Iterable string    `json:"iterable"`
			Body     []ASTNode `json:"body"`
		}{n.Kind, n.Var, n.Iterable, body})
	}
	return nil, &json.MarshalerError{Err: errUnknownKind(n.Kind)}
}

func (n *ASTNode) UnmarshalJSON(data []byte) error {
	var env struct {
		Kind       string     `json:"kind"`
		Content    string     `json:"content"`
		Field      string     `json:"field"`
		Condition  *Condition `json:"condition"`
		ThenBranch []ASTNode  `json:"then_branch"`
		ElseBranch []ASTNode  `json:"else_branch"`
		Var        string     `json:"var"`
		Iterable   string     `json:"iterable"`
		Body       []ASTNode  `json:"body"`
	}
	if err := json.Unmarshal(data, &env); err != nil {
		return err
	}
	*n = ASTNode{
		Kind:       env.Kind,
		Content:    env.Content,
		Field:      env.Field,
		Condition:  env.Condition,
		ThenBranch: env.ThenBranch,
		ElseBranch: env.ElseBranch,
		Var:        env.Var,
		Iterable:   env.Iterable,
		Body:       env.Body,
	}
	return nil
}

// ---------------------------------------------------------------------------
// TIR — discriminated by Kind.
// ---------------------------------------------------------------------------

type TIRNode struct {
	Kind      string
	Content   string
	Field     string
	Resolved  any
	Condition bool
	Branch    []TIRNode
	Var       string
	Items     [][]TIRNode
}

func (n TIRNode) MarshalJSON() ([]byte, error) {
	switch n.Kind {
	case "text":
		return json.Marshal(struct {
			Kind    string `json:"kind"`
			Content string `json:"content"`
		}{n.Kind, n.Content})
	case "expr":
		// Resolved must always appear, even when null.
		return json.Marshal(struct {
			Kind     string `json:"kind"`
			Field    string `json:"field"`
			Resolved any    `json:"resolved"`
		}{n.Kind, n.Field, n.Resolved})
	case "if":
		branch := n.Branch
		if branch == nil {
			branch = []TIRNode{}
		}
		return json.Marshal(struct {
			Kind      string    `json:"kind"`
			Condition bool      `json:"condition"`
			Branch    []TIRNode `json:"branch"`
		}{n.Kind, n.Condition, branch})
	case "foreach":
		items := n.Items
		if items == nil {
			items = [][]TIRNode{}
		}
		return json.Marshal(struct {
			Kind  string      `json:"kind"`
			Var   string      `json:"var"`
			Items [][]TIRNode `json:"items"`
		}{n.Kind, n.Var, items})
	}
	return nil, &json.MarshalerError{Err: errUnknownKind(n.Kind)}
}

func (n *TIRNode) UnmarshalJSON(data []byte) error {
	var env struct {
		Kind      string      `json:"kind"`
		Content   string      `json:"content"`
		Field     string      `json:"field"`
		Resolved  any         `json:"resolved"`
		Condition bool        `json:"condition"`
		Branch    []TIRNode   `json:"branch"`
		Var       string      `json:"var"`
		Items     [][]TIRNode `json:"items"`
	}
	if err := json.Unmarshal(data, &env); err != nil {
		return err
	}
	*n = TIRNode{
		Kind:      env.Kind,
		Content:   env.Content,
		Field:     env.Field,
		Resolved:  env.Resolved,
		Condition: env.Condition,
		Branch:    env.Branch,
		Var:       env.Var,
		Items:     env.Items,
	}
	return nil
}

type TIRResult struct {
	TemplateID string    `json:"template_id"`
	SchemaID   string    `json:"schema_id"`
	Nodes      []TIRNode `json:"nodes"`
}

// ---------------------------------------------------------------------------

type errUnknownKind string

func (e errUnknownKind) Error() string { return "unknown kind: " + string(e) }
