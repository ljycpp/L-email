import { asyncRouterMap, constantRouterMap, loadDynamicRoutes } from 'src/router';

function hasPermission(roles, route) {
  if (route.meta && route.meta.role) {
    return roles.some(role => route.meta.role.indexOf(role) >= 0);
  }
  return true;
}

const permission = {
  state: {
    routers: constantRouterMap,
    addRouters: []
  },

  mutations: {
    SET_ROUTERS: (state, routers) => {
      state.addRouters = routers;
      state.routers = constantRouterMap.concat(routers);
    }
  },

  actions: {
    GenerateRoutes({ commit }, data) {
      return new Promise(resolve => {
        const { roles } = data;
        loadDynamicRoutes().then(() => {
          const accessedRouters = asyncRouterMap.filter(v => {
            if (roles.indexOf('admin') >= 0) return true;
            if (hasPermission(roles, v)) {
              if (v.children && v.children.length > 0) {
                v.children = v.children.filter(child => hasPermission(roles, child));
                return v;
              }
              return v;
            }
            return false;
          });
          commit('SET_ROUTERS', accessedRouters);
          resolve();
        });
      });
    }
  }
};

export default permission;
