import {createRouter, createWebHistory} from "vue-router";
import OverviewView from "@/views/OverviewView.vue";
import LibraryView from "@/views/LibraryView.vue";
import StudyView from "@/views/StudyView.vue";
import CareerView from "@/views/CareerView.vue";

const routes = [
  {path: "/", name: "overview", component: OverviewView, meta: {tab: "overview"}},
  {path: "/library", name: "library", component: LibraryView, meta: {tab: "knowledge"}},
  {path: "/study", name: "study", component: StudyView, meta: {tab: "chat"}},
  {path: "/career", name: "career", component: CareerView, meta: {tab: "jobs"}},
  {path: "/:pathMatch(.*)*", redirect: "/"}
];

export default createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior() {
    return {top: 0};
  }
});
