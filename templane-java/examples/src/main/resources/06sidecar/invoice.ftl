INVOICE #${invoice_number}
--------------------------------

Bill to: ${customer.name}
         ${customer.email}

<#list line_items as item>
  ${item.description?right_pad(28)} ${item.qty} × $${item.unit_price?string["0.00"]}
</#list>
--------------------------------
Total: $${total?string["0.00"]}
