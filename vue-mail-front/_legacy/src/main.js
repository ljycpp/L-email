import Vue from 'vue';
import App from './App';
import router from './router';
import store from './store';
import ElementUI from 'element-ui';
import 'element-ui/lib/theme-default/index.css';
import 'assets/custom-theme/index.css';
import NProgress from 'nprogress';
import 'nprogress/nprogress.css';
import 'normalize.css/normalize.css';
import 'styles/index.scss';
import 'components/Icon-svg/index';
import 'assets/iconfont/iconfont';
import * as filters from './filters';
import Multiselect from 'vue-multiselect';
import 'vue-multiselect/dist/vue-multiselect.min.css';
import Sticky from 'components/Sticky';
import vueWaves from './directive/waves';
import errLog from 'store/errLog';

Vue.component('multiselect', Multiselect);
Vue.component('Sticky', Sticky);
Vue.use(ElementUI);
Vue.use(vueWaves);

Object.keys(filters).forEach(key => {
  Vue.filter(key, filters[key]);
});

function hasPermission(roles, permissionRoles) {
  if (roles.indexOf('admin') >= 0) {
    return true;
  }
  if (!permissionRoles) {
    return true;
  }
  return roles.some(role => permissionRoles.indexOf(role) >= 0);
}

const whiteList = ['/login', '/authredirect'];

router.beforeEach((to, from, next) => {
  NProgress.start();

  if (store.getters.token) {
    if (to.path === '/login') {
      next({ path: '/' });
      return;
    }

    if (store.getters.roles.length === 0) {
      store.dispatch('GetInfo').then(res => {
        const roles = res.data.role;
        store.dispatch('GenerateRoutes', { roles }).then(() => {
          router.addRoutes(store.getters.addRouters);
          next(to.path);
        });
      }).catch(err => {
        console.log(err);
        store.dispatch('FedLogOut').then(() => {
          next('/login');
          NProgress.done();
        });
      });
      return;
    }

    if (hasPermission(store.getters.roles, to.meta.role)) {
      next();
    } else {
      next({ path: '/401', query: { noGoBack: true } });
    }
    return;
  }

  if (whiteList.indexOf(to.path) !== -1) {
    next();
  } else {
    next('/login');
    NProgress.done();
  }
});

router.afterEach(() => {
  NProgress.done();
});

if (process.env === 'production') {
  Vue.config.errorHandler = function(err, vm) {
    console.log(err, window.location.href);
    errLog.pushLog({
      err,
      url: window.location.href,
      vm
    });
  };
}

new Vue({
  router,
  store,
  render: h => h(App)
}).$mount('#app');
