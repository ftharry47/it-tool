import{c as s,h as d,j as o,v as m,w as h}from"./index-CX3Z7TzV.js";import{L as p}from"./react-B1z9iAW_.js";import{U as x}from"./users-BaEs5sOy.js";import{M as g}from"./map-pin-BDawX7BQ.js";import{F as u}from"./file-spreadsheet-BUvhSDlt.js";import"./msal-BkvdL5aX.js";/**
 * @license lucide-react v0.436.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const k=s("Bot",[["path",{d:"M12 8V4H8",key:"hb8ula"}],["rect",{width:"16",height:"12",x:"4",y:"8",rx:"2",key:"enze0r"}],["path",{d:"M2 14h2",key:"vft8re"}],["path",{d:"M20 14h2",key:"4cs60a"}],["path",{d:"M15 13v2",key:"1xurst"}],["path",{d:"M9 13v2",key:"rq6x2g"}]]);/**
 * @license lucide-react v0.436.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const b=s("Workflow",[["rect",{width:"8",height:"8",x:"3",y:"3",rx:"2",key:"by2w9f"}],["path",{d:"M7 11v4a2 2 0 0 0 2 2h4",key:"xkn7yn"}],["rect",{width:"8",height:"8",x:"13",y:"13",rx:"2",key:"1cgmvn"}]]);function A(){const{currentUser:e}=d(),a=(e==null?void 0:e.roles.includes("SUPER_ADMIN"))??!1,r=[{to:"/admin/users",icon:x,label:"Users"},{to:"/admin/catalog",icon:m,label:"Catalog"},{to:"/admin/workflows",icon:b,label:"Workflows"},{to:"/admin/automation",icon:k,label:"Automation"},{to:"/admin/locations",icon:g,label:"Locations"}],t=[{to:"/admin/import-tickets",icon:u,label:"Import Tickets"},{to:"/admin/how-it-works",icon:h,label:"How It Works"}],n=a?[...r,...t]:t;return o.jsx("div",{className:"min-h-full bg-background p-6 text-foreground",children:o.jsxs("div",{className:"mx-auto max-w-6xl space-y-6",children:[o.jsx("h1",{className:"text-2xl font-semibold tracking-tight",children:"Administration"}),o.jsx("div",{className:"grid gap-3 sm:grid-cols-2 lg:grid-cols-4",children:n.map(({to:i,icon:l,label:c})=>o.jsxs(p,{to:i,className:"rounded-md border border-border bg-card p-4 text-sm font-medium hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring",children:[o.jsx(l,{className:"mb-2 h-5 w-5"}),c]},i))})]})})}export{A as AdminHome};
//# sourceMappingURL=AdminHome-DQXeQTHB.js.map
