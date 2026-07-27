import {createApp} from "vue";
import App from "./App.vue";
import router from "./router/index.js";
import "./styles/tokens.css";
import "./styles/app.css";

const app = createApp(App);
app.use(router);

const instance = app.mount("#app");
window.__zhituApp = instance;
window.__aikbApp = instance;
