import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import '@arco-design/web-react/dist/css/arco.css'

import App from './App'
import './styles/index.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
