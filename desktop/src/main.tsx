import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import "./ui/styles.css";

/** The desktop widget window loads the same page with ?widget. */
export const isWidget = new URLSearchParams(location.search).has("widget");
if (isWidget) document.body.classList.add("widget-body");

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App widget={isWidget} />
  </StrictMode>,
);
