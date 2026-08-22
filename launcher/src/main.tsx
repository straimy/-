import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import LauncherUpdateGate from "./LauncherUpdateGate";
import "./styles.css";
import "./polish.css";
import "./training.css";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <LauncherUpdateGate>
      <App />
    </LauncherUpdateGate>
  </React.StrictMode>
);