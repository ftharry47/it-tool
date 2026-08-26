import React from 'https://cdn.jsdelivr.net/npm/react@18.3.1/+esm'
import { Routes, Route } from 'https://cdn.jsdelivr.net/npm/react-router-dom@6.26.0/+esm'
import HomePage from './pages/HomePage.js'
import FormPage from './pages/FormPage.js'
import DashboardPage from './pages/DashboardPage.js'

export default function App() {
  return React.createElement(Routes, null,
    React.createElement(Route, { path: '/', element: React.createElement(HomePage) }),
    React.createElement(Route, { path: '/form', element: React.createElement(FormPage) }),
    React.createElement(Route, { path: '/dashboard', element: React.createElement(DashboardPage) })
  )
}


