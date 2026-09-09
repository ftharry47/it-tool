---
description: Sidebar navigation rules for the ITSM portal
trigger: ["frontend", "react", "routing", "sidebar", "RoleNav"]
---

# Sidebar / Navigation UX Rules

## Approved pattern

The `RoleNav.tsx` sidebar in `v4/src/main/frontend/src/components/layout/RoleNav.tsx` only renders top-level list routes.

It filters with:

```tsx
const navRoutes = routes.filter((route) => !route.path.includes(':') && !route.hidden)
```

- Parameterized / detail routes (any route path containing `:`) are intentionally hidden from the sidebar.
- Detail pages must remain reachable only via row/card clicks or direct URL navigation.
- This matches the Jira / ServiceNow UX pattern and was explicitly approved by the user.

## Rules to follow

- Do **not** remove the `:path` filter from `RoleNav.tsx`.
- Do **not** add parameterized/detail routes (e.g., `dashboard/incidents/:id`, `dashboard/projects/:id`) to the sidebar.
- Keep list routes in the sidebar; open detail views from table rows, kanban cards, or other list item clicks.
- Avoid unrequested UX or structural navigation changes; get explicit approval before altering the sidebar filtering behavior or global route structure.
