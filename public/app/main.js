import React from 'https://cdn.jsdelivr.net/npm/react@18.3.1/+esm'
import ReactDOM from 'https://cdn.jsdelivr.net/npm/react-dom@18.3.1/client/+esm'
import { BrowserRouter } from 'https://cdn.jsdelivr.net/npm/react-router-dom@6.26.0/+esm'
import App from './App.js'

function showErr(msg, stack) {
  const d = document.getElementById('diag')
  if (d) { d.classList.remove('hidden'); d.textContent = msg + (stack ? '\n' + stack : '') }
}

try {
  const root = ReactDOM.createRoot(document.getElementById('root'))
  root.render(
    React.createElement(BrowserRouter, null, React.createElement(App, null))
  )
} catch (e) {
  showErr(e.message, e.stack)
}


