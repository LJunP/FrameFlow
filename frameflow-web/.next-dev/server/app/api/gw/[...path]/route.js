/*
 * ATTENTION: An "eval-source-map" devtool has been used.
 * This devtool is neither made for production nor for readable output files.
 * It uses "eval()" calls to create a separate source file with attached SourceMaps in the browser devtools.
 * If you are trying to read the output file, select a different devtool (https://webpack.js.org/configuration/devtool/)
 * or disable the default devtool with "devtool: false".
 * If you are looking for production-ready output files, see mode: "production" (https://webpack.js.org/configuration/mode/).
 */
(() => {
var exports = {};
exports.id = "app/api/gw/[...path]/route";
exports.ids = ["app/api/gw/[...path]/route"];
exports.modules = {

/***/ "(rsc)/./app/api/gw/[...path]/route.ts":
/*!***************************************!*\
  !*** ./app/api/gw/[...path]/route.ts ***!
  \***************************************/
/***/ ((__unused_webpack_module, __webpack_exports__, __webpack_require__) => {

"use strict";
eval("__webpack_require__.r(__webpack_exports__);\n/* harmony export */ __webpack_require__.d(__webpack_exports__, {\n/* harmony export */   DELETE: () => (/* binding */ DELETE),\n/* harmony export */   GET: () => (/* binding */ GET),\n/* harmony export */   POST: () => (/* binding */ POST),\n/* harmony export */   PUT: () => (/* binding */ PUT)\n/* harmony export */ });\n/* harmony import */ var next_server__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! next/server */ \"(rsc)/./node_modules/next/dist/api/server.js\");\n\n// 同源通用代理：浏览器 → Next(route handler) → Java API。\n// 收益一：前端与 Java 之间零 CORS 配置；\n// 收益二：API_BASE 是服务端环境变量，浏览器永远看不到后端拓扑。\nconst API_BASE = process.env.API_BASE ?? 'http://127.0.0.1:18080';\nasync function forward(req, path) {\n    const url = `${API_BASE}/api/v1/${path.join('/')}${req.nextUrl.search}`;\n    const headers = new Headers();\n    const auth = req.headers.get('authorization');\n    if (auth) headers.set('Authorization', auth);\n    const contentType = req.headers.get('content-type');\n    if (contentType) headers.set('Content-Type', contentType);\n    const init = {\n        method: req.method,\n        headers\n    };\n    if (![\n        'GET',\n        'HEAD'\n    ].includes(req.method)) {\n        init.body = await req.arrayBuffer();\n    }\n    const upstream = await fetch(url, init);\n    const body = await upstream.arrayBuffer();\n    const resp = new next_server__WEBPACK_IMPORTED_MODULE_0__.NextResponse(body, {\n        status: upstream.status\n    });\n    const upstreamType = upstream.headers.get('content-type');\n    if (upstreamType) resp.headers.set('Content-Type', upstreamType);\n    return resp;\n}\nasync function GET(req, ctx) {\n    return forward(req, (await ctx.params).path);\n}\nasync function POST(req, ctx) {\n    return forward(req, (await ctx.params).path);\n}\nasync function PUT(req, ctx) {\n    return forward(req, (await ctx.params).path);\n}\nasync function DELETE(req, ctx) {\n    return forward(req, (await ctx.params).path);\n}\n//# sourceURL=[module]\n//# sourceMappingURL=data:application/json;charset=utf-8;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoiKHJzYykvLi9hcHAvYXBpL2d3L1suLi5wYXRoXS9yb3V0ZS50cyIsIm1hcHBpbmdzIjoiOzs7Ozs7OztBQUF3RDtBQUV4RCwrQ0FBK0M7QUFDL0MsNEJBQTRCO0FBQzVCLHNDQUFzQztBQUV0QyxNQUFNQyxXQUFXQyxRQUFRQyxHQUFHLENBQUNGLFFBQVEsSUFBSTtBQUV6QyxlQUFlRyxRQUFRQyxHQUFnQixFQUFFQyxJQUFjO0lBQ3JELE1BQU1DLE1BQU0sR0FBR04sU0FBUyxRQUFRLEVBQUVLLEtBQUtFLElBQUksQ0FBQyxPQUFPSCxJQUFJSSxPQUFPLENBQUNDLE1BQU0sRUFBRTtJQUN2RSxNQUFNQyxVQUFVLElBQUlDO0lBQ3BCLE1BQU1DLE9BQU9SLElBQUlNLE9BQU8sQ0FBQ0csR0FBRyxDQUFDO0lBQzdCLElBQUlELE1BQU1GLFFBQVFJLEdBQUcsQ0FBQyxpQkFBaUJGO0lBQ3ZDLE1BQU1HLGNBQWNYLElBQUlNLE9BQU8sQ0FBQ0csR0FBRyxDQUFDO0lBQ3BDLElBQUlFLGFBQWFMLFFBQVFJLEdBQUcsQ0FBQyxnQkFBZ0JDO0lBRTdDLE1BQU1DLE9BQW9CO1FBQUVDLFFBQVFiLElBQUlhLE1BQU07UUFBRVA7SUFBUTtJQUN4RCxJQUFJLENBQUM7UUFBQztRQUFPO0tBQU8sQ0FBQ1EsUUFBUSxDQUFDZCxJQUFJYSxNQUFNLEdBQUc7UUFDekNELEtBQUtHLElBQUksR0FBRyxNQUFNZixJQUFJZ0IsV0FBVztJQUNuQztJQUNBLE1BQU1DLFdBQVcsTUFBTUMsTUFBTWhCLEtBQUtVO0lBQ2xDLE1BQU1HLE9BQU8sTUFBTUUsU0FBU0QsV0FBVztJQUN2QyxNQUFNRyxPQUFPLElBQUl4QixxREFBWUEsQ0FBQ29CLE1BQU07UUFBRUssUUFBUUgsU0FBU0csTUFBTTtJQUFDO0lBQzlELE1BQU1DLGVBQWVKLFNBQVNYLE9BQU8sQ0FBQ0csR0FBRyxDQUFDO0lBQzFDLElBQUlZLGNBQWNGLEtBQUtiLE9BQU8sQ0FBQ0ksR0FBRyxDQUFDLGdCQUFnQlc7SUFDbkQsT0FBT0Y7QUFDVDtBQUlPLGVBQWVHLElBQUl0QixHQUFnQixFQUFFdUIsR0FBUTtJQUNsRCxPQUFPeEIsUUFBUUMsS0FBSyxDQUFDLE1BQU11QixJQUFJQyxNQUFNLEVBQUV2QixJQUFJO0FBQzdDO0FBQ08sZUFBZXdCLEtBQUt6QixHQUFnQixFQUFFdUIsR0FBUTtJQUNuRCxPQUFPeEIsUUFBUUMsS0FBSyxDQUFDLE1BQU11QixJQUFJQyxNQUFNLEVBQUV2QixJQUFJO0FBQzdDO0FBQ08sZUFBZXlCLElBQUkxQixHQUFnQixFQUFFdUIsR0FBUTtJQUNsRCxPQUFPeEIsUUFBUUMsS0FBSyxDQUFDLE1BQU11QixJQUFJQyxNQUFNLEVBQUV2QixJQUFJO0FBQzdDO0FBQ08sZUFBZTBCLE9BQU8zQixHQUFnQixFQUFFdUIsR0FBUTtJQUNyRCxPQUFPeEIsUUFBUUMsS0FBSyxDQUFDLE1BQU11QixJQUFJQyxNQUFNLEVBQUV2QixJQUFJO0FBQzdDIiwic291cmNlcyI6WyIvVXNlcnMvbGlqdW5wZW5nL0Rlc2t0b3Avb3Blbl9zb3VyY2VfcHJvamVjdC9GcmFtZUZsb3cvZnJhbWVmbG93LXdlYi9hcHAvYXBpL2d3L1suLi5wYXRoXS9yb3V0ZS50cyJdLCJzb3VyY2VzQ29udGVudCI6WyJpbXBvcnQgeyBOZXh0UmVxdWVzdCwgTmV4dFJlc3BvbnNlIH0gZnJvbSAnbmV4dC9zZXJ2ZXInO1xuXG4vLyDlkIzmupDpgJrnlKjku6PnkIbvvJrmtY/op4jlmagg4oaSIE5leHQocm91dGUgaGFuZGxlcikg4oaSIEphdmEgQVBJ44CCXG4vLyDmlLbnm4rkuIDvvJrliY3nq6/kuI4gSmF2YSDkuYvpl7Tpm7YgQ09SUyDphY3nva7vvJtcbi8vIOaUtuebiuS6jO+8mkFQSV9CQVNFIOaYr+acjeWKoeerr+eOr+Wig+WPmOmHj++8jOa1j+iniOWZqOawuOi/nOeci+S4jeWIsOWQjuerr+aLk+aJkeOAglxuXG5jb25zdCBBUElfQkFTRSA9IHByb2Nlc3MuZW52LkFQSV9CQVNFID8/ICdodHRwOi8vMTI3LjAuMC4xOjE4MDgwJztcblxuYXN5bmMgZnVuY3Rpb24gZm9yd2FyZChyZXE6IE5leHRSZXF1ZXN0LCBwYXRoOiBzdHJpbmdbXSkge1xuICBjb25zdCB1cmwgPSBgJHtBUElfQkFTRX0vYXBpL3YxLyR7cGF0aC5qb2luKCcvJyl9JHtyZXEubmV4dFVybC5zZWFyY2h9YDtcbiAgY29uc3QgaGVhZGVycyA9IG5ldyBIZWFkZXJzKCk7XG4gIGNvbnN0IGF1dGggPSByZXEuaGVhZGVycy5nZXQoJ2F1dGhvcml6YXRpb24nKTtcbiAgaWYgKGF1dGgpIGhlYWRlcnMuc2V0KCdBdXRob3JpemF0aW9uJywgYXV0aCk7XG4gIGNvbnN0IGNvbnRlbnRUeXBlID0gcmVxLmhlYWRlcnMuZ2V0KCdjb250ZW50LXR5cGUnKTtcbiAgaWYgKGNvbnRlbnRUeXBlKSBoZWFkZXJzLnNldCgnQ29udGVudC1UeXBlJywgY29udGVudFR5cGUpO1xuXG4gIGNvbnN0IGluaXQ6IFJlcXVlc3RJbml0ID0geyBtZXRob2Q6IHJlcS5tZXRob2QsIGhlYWRlcnMgfTtcbiAgaWYgKCFbJ0dFVCcsICdIRUFEJ10uaW5jbHVkZXMocmVxLm1ldGhvZCkpIHtcbiAgICBpbml0LmJvZHkgPSBhd2FpdCByZXEuYXJyYXlCdWZmZXIoKTtcbiAgfVxuICBjb25zdCB1cHN0cmVhbSA9IGF3YWl0IGZldGNoKHVybCwgaW5pdCk7XG4gIGNvbnN0IGJvZHkgPSBhd2FpdCB1cHN0cmVhbS5hcnJheUJ1ZmZlcigpO1xuICBjb25zdCByZXNwID0gbmV3IE5leHRSZXNwb25zZShib2R5LCB7IHN0YXR1czogdXBzdHJlYW0uc3RhdHVzIH0pO1xuICBjb25zdCB1cHN0cmVhbVR5cGUgPSB1cHN0cmVhbS5oZWFkZXJzLmdldCgnY29udGVudC10eXBlJyk7XG4gIGlmICh1cHN0cmVhbVR5cGUpIHJlc3AuaGVhZGVycy5zZXQoJ0NvbnRlbnQtVHlwZScsIHVwc3RyZWFtVHlwZSk7XG4gIHJldHVybiByZXNwO1xufVxuXG50eXBlIEN0eCA9IHsgcGFyYW1zOiBQcm9taXNlPHsgcGF0aDogc3RyaW5nW10gfT4gfTtcblxuZXhwb3J0IGFzeW5jIGZ1bmN0aW9uIEdFVChyZXE6IE5leHRSZXF1ZXN0LCBjdHg6IEN0eCkge1xuICByZXR1cm4gZm9yd2FyZChyZXEsIChhd2FpdCBjdHgucGFyYW1zKS5wYXRoKTtcbn1cbmV4cG9ydCBhc3luYyBmdW5jdGlvbiBQT1NUKHJlcTogTmV4dFJlcXVlc3QsIGN0eDogQ3R4KSB7XG4gIHJldHVybiBmb3J3YXJkKHJlcSwgKGF3YWl0IGN0eC5wYXJhbXMpLnBhdGgpO1xufVxuZXhwb3J0IGFzeW5jIGZ1bmN0aW9uIFBVVChyZXE6IE5leHRSZXF1ZXN0LCBjdHg6IEN0eCkge1xuICByZXR1cm4gZm9yd2FyZChyZXEsIChhd2FpdCBjdHgucGFyYW1zKS5wYXRoKTtcbn1cbmV4cG9ydCBhc3luYyBmdW5jdGlvbiBERUxFVEUocmVxOiBOZXh0UmVxdWVzdCwgY3R4OiBDdHgpIHtcbiAgcmV0dXJuIGZvcndhcmQocmVxLCAoYXdhaXQgY3R4LnBhcmFtcykucGF0aCk7XG59XG4iXSwibmFtZXMiOlsiTmV4dFJlc3BvbnNlIiwiQVBJX0JBU0UiLCJwcm9jZXNzIiwiZW52IiwiZm9yd2FyZCIsInJlcSIsInBhdGgiLCJ1cmwiLCJqb2luIiwibmV4dFVybCIsInNlYXJjaCIsImhlYWRlcnMiLCJIZWFkZXJzIiwiYXV0aCIsImdldCIsInNldCIsImNvbnRlbnRUeXBlIiwiaW5pdCIsIm1ldGhvZCIsImluY2x1ZGVzIiwiYm9keSIsImFycmF5QnVmZmVyIiwidXBzdHJlYW0iLCJmZXRjaCIsInJlc3AiLCJzdGF0dXMiLCJ1cHN0cmVhbVR5cGUiLCJHRVQiLCJjdHgiLCJwYXJhbXMiLCJQT1NUIiwiUFVUIiwiREVMRVRFIl0sImlnbm9yZUxpc3QiOltdLCJzb3VyY2VSb290IjoiIn0=\n//# sourceURL=webpack-internal:///(rsc)/./app/api/gw/[...path]/route.ts\n");

/***/ }),

/***/ "(rsc)/./node_modules/next/dist/build/webpack/loaders/next-app-loader/index.js?name=app%2Fapi%2Fgw%2F%5B...path%5D%2Froute&page=%2Fapi%2Fgw%2F%5B...path%5D%2Froute&appPaths=&pagePath=private-next-app-dir%2Fapi%2Fgw%2F%5B...path%5D%2Froute.ts&appDir=%2FUsers%2Flijunpeng%2FDesktop%2Fopen_source_project%2FFrameFlow%2Fframeflow-web%2Fapp&pageExtensions=tsx&pageExtensions=ts&pageExtensions=jsx&pageExtensions=js&rootDir=%2FUsers%2Flijunpeng%2FDesktop%2Fopen_source_project%2FFrameFlow%2Fframeflow-web&isDev=true&tsconfigPath=tsconfig.json&basePath=&assetPrefix=&nextConfigOutput=&preferredRegion=&middlewareConfig=e30%3D!":
/*!********************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************!*\
  !*** ./node_modules/next/dist/build/webpack/loaders/next-app-loader/index.js?name=app%2Fapi%2Fgw%2F%5B...path%5D%2Froute&page=%2Fapi%2Fgw%2F%5B...path%5D%2Froute&appPaths=&pagePath=private-next-app-dir%2Fapi%2Fgw%2F%5B...path%5D%2Froute.ts&appDir=%2FUsers%2Flijunpeng%2FDesktop%2Fopen_source_project%2FFrameFlow%2Fframeflow-web%2Fapp&pageExtensions=tsx&pageExtensions=ts&pageExtensions=jsx&pageExtensions=js&rootDir=%2FUsers%2Flijunpeng%2FDesktop%2Fopen_source_project%2FFrameFlow%2Fframeflow-web&isDev=true&tsconfigPath=tsconfig.json&basePath=&assetPrefix=&nextConfigOutput=&preferredRegion=&middlewareConfig=e30%3D! ***!
  \********************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************************/
/***/ ((__unused_webpack_module, __webpack_exports__, __webpack_require__) => {

"use strict";
eval("__webpack_require__.r(__webpack_exports__);\n/* harmony export */ __webpack_require__.d(__webpack_exports__, {\n/* harmony export */   patchFetch: () => (/* binding */ patchFetch),\n/* harmony export */   routeModule: () => (/* binding */ routeModule),\n/* harmony export */   serverHooks: () => (/* binding */ serverHooks),\n/* harmony export */   workAsyncStorage: () => (/* binding */ workAsyncStorage),\n/* harmony export */   workUnitAsyncStorage: () => (/* binding */ workUnitAsyncStorage)\n/* harmony export */ });\n/* harmony import */ var next_dist_server_route_modules_app_route_module_compiled__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! next/dist/server/route-modules/app-route/module.compiled */ \"(rsc)/./node_modules/next/dist/server/route-modules/app-route/module.compiled.js\");\n/* harmony import */ var next_dist_server_route_modules_app_route_module_compiled__WEBPACK_IMPORTED_MODULE_0___default = /*#__PURE__*/__webpack_require__.n(next_dist_server_route_modules_app_route_module_compiled__WEBPACK_IMPORTED_MODULE_0__);\n/* harmony import */ var next_dist_server_route_kind__WEBPACK_IMPORTED_MODULE_1__ = __webpack_require__(/*! next/dist/server/route-kind */ \"(rsc)/./node_modules/next/dist/server/route-kind.js\");\n/* harmony import */ var next_dist_server_lib_patch_fetch__WEBPACK_IMPORTED_MODULE_2__ = __webpack_require__(/*! next/dist/server/lib/patch-fetch */ \"(rsc)/./node_modules/next/dist/server/lib/patch-fetch.js\");\n/* harmony import */ var next_dist_server_lib_patch_fetch__WEBPACK_IMPORTED_MODULE_2___default = /*#__PURE__*/__webpack_require__.n(next_dist_server_lib_patch_fetch__WEBPACK_IMPORTED_MODULE_2__);\n/* harmony import */ var _Users_lijunpeng_Desktop_open_source_project_FrameFlow_frameflow_web_app_api_gw_path_route_ts__WEBPACK_IMPORTED_MODULE_3__ = __webpack_require__(/*! ./app/api/gw/[...path]/route.ts */ \"(rsc)/./app/api/gw/[...path]/route.ts\");\n\n\n\n\n// We inject the nextConfigOutput here so that we can use them in the route\n// module.\nconst nextConfigOutput = \"\"\nconst routeModule = new next_dist_server_route_modules_app_route_module_compiled__WEBPACK_IMPORTED_MODULE_0__.AppRouteRouteModule({\n    definition: {\n        kind: next_dist_server_route_kind__WEBPACK_IMPORTED_MODULE_1__.RouteKind.APP_ROUTE,\n        page: \"/api/gw/[...path]/route\",\n        pathname: \"/api/gw/[...path]\",\n        filename: \"route\",\n        bundlePath: \"app/api/gw/[...path]/route\"\n    },\n    resolvedPagePath: \"/Users/lijunpeng/Desktop/open_source_project/FrameFlow/frameflow-web/app/api/gw/[...path]/route.ts\",\n    nextConfigOutput,\n    userland: _Users_lijunpeng_Desktop_open_source_project_FrameFlow_frameflow_web_app_api_gw_path_route_ts__WEBPACK_IMPORTED_MODULE_3__\n});\n// Pull out the exports that we need to expose from the module. This should\n// be eliminated when we've moved the other routes to the new format. These\n// are used to hook into the route.\nconst { workAsyncStorage, workUnitAsyncStorage, serverHooks } = routeModule;\nfunction patchFetch() {\n    return (0,next_dist_server_lib_patch_fetch__WEBPACK_IMPORTED_MODULE_2__.patchFetch)({\n        workAsyncStorage,\n        workUnitAsyncStorage\n    });\n}\n\n\n//# sourceMappingURL=app-route.js.map//# sourceURL=[module]\n//# sourceMappingURL=data:application/json;charset=utf-8;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoiKHJzYykvLi9ub2RlX21vZHVsZXMvbmV4dC9kaXN0L2J1aWxkL3dlYnBhY2svbG9hZGVycy9uZXh0LWFwcC1sb2FkZXIvaW5kZXguanM/bmFtZT1hcHAlMkZhcGklMkZndyUyRiU1Qi4uLnBhdGglNUQlMkZyb3V0ZSZwYWdlPSUyRmFwaSUyRmd3JTJGJTVCLi4ucGF0aCU1RCUyRnJvdXRlJmFwcFBhdGhzPSZwYWdlUGF0aD1wcml2YXRlLW5leHQtYXBwLWRpciUyRmFwaSUyRmd3JTJGJTVCLi4ucGF0aCU1RCUyRnJvdXRlLnRzJmFwcERpcj0lMkZVc2VycyUyRmxpanVucGVuZyUyRkRlc2t0b3AlMkZvcGVuX3NvdXJjZV9wcm9qZWN0JTJGRnJhbWVGbG93JTJGZnJhbWVmbG93LXdlYiUyRmFwcCZwYWdlRXh0ZW5zaW9ucz10c3gmcGFnZUV4dGVuc2lvbnM9dHMmcGFnZUV4dGVuc2lvbnM9anN4JnBhZ2VFeHRlbnNpb25zPWpzJnJvb3REaXI9JTJGVXNlcnMlMkZsaWp1bnBlbmclMkZEZXNrdG9wJTJGb3Blbl9zb3VyY2VfcHJvamVjdCUyRkZyYW1lRmxvdyUyRmZyYW1lZmxvdy13ZWImaXNEZXY9dHJ1ZSZ0c2NvbmZpZ1BhdGg9dHNjb25maWcuanNvbiZiYXNlUGF0aD0mYXNzZXRQcmVmaXg9Jm5leHRDb25maWdPdXRwdXQ9JnByZWZlcnJlZFJlZ2lvbj0mbWlkZGxld2FyZUNvbmZpZz1lMzAlM0QhIiwibWFwcGluZ3MiOiI7Ozs7Ozs7Ozs7Ozs7O0FBQStGO0FBQ3ZDO0FBQ3FCO0FBQ2tEO0FBQy9IO0FBQ0E7QUFDQTtBQUNBLHdCQUF3Qix5R0FBbUI7QUFDM0M7QUFDQSxjQUFjLGtFQUFTO0FBQ3ZCO0FBQ0E7QUFDQTtBQUNBO0FBQ0EsS0FBSztBQUNMO0FBQ0E7QUFDQSxZQUFZO0FBQ1osQ0FBQztBQUNEO0FBQ0E7QUFDQTtBQUNBLFFBQVEsc0RBQXNEO0FBQzlEO0FBQ0EsV0FBVyw0RUFBVztBQUN0QjtBQUNBO0FBQ0EsS0FBSztBQUNMO0FBQzBGOztBQUUxRiIsInNvdXJjZXMiOlsiIl0sInNvdXJjZXNDb250ZW50IjpbImltcG9ydCB7IEFwcFJvdXRlUm91dGVNb2R1bGUgfSBmcm9tIFwibmV4dC9kaXN0L3NlcnZlci9yb3V0ZS1tb2R1bGVzL2FwcC1yb3V0ZS9tb2R1bGUuY29tcGlsZWRcIjtcbmltcG9ydCB7IFJvdXRlS2luZCB9IGZyb20gXCJuZXh0L2Rpc3Qvc2VydmVyL3JvdXRlLWtpbmRcIjtcbmltcG9ydCB7IHBhdGNoRmV0Y2ggYXMgX3BhdGNoRmV0Y2ggfSBmcm9tIFwibmV4dC9kaXN0L3NlcnZlci9saWIvcGF0Y2gtZmV0Y2hcIjtcbmltcG9ydCAqIGFzIHVzZXJsYW5kIGZyb20gXCIvVXNlcnMvbGlqdW5wZW5nL0Rlc2t0b3Avb3Blbl9zb3VyY2VfcHJvamVjdC9GcmFtZUZsb3cvZnJhbWVmbG93LXdlYi9hcHAvYXBpL2d3L1suLi5wYXRoXS9yb3V0ZS50c1wiO1xuLy8gV2UgaW5qZWN0IHRoZSBuZXh0Q29uZmlnT3V0cHV0IGhlcmUgc28gdGhhdCB3ZSBjYW4gdXNlIHRoZW0gaW4gdGhlIHJvdXRlXG4vLyBtb2R1bGUuXG5jb25zdCBuZXh0Q29uZmlnT3V0cHV0ID0gXCJcIlxuY29uc3Qgcm91dGVNb2R1bGUgPSBuZXcgQXBwUm91dGVSb3V0ZU1vZHVsZSh7XG4gICAgZGVmaW5pdGlvbjoge1xuICAgICAgICBraW5kOiBSb3V0ZUtpbmQuQVBQX1JPVVRFLFxuICAgICAgICBwYWdlOiBcIi9hcGkvZ3cvWy4uLnBhdGhdL3JvdXRlXCIsXG4gICAgICAgIHBhdGhuYW1lOiBcIi9hcGkvZ3cvWy4uLnBhdGhdXCIsXG4gICAgICAgIGZpbGVuYW1lOiBcInJvdXRlXCIsXG4gICAgICAgIGJ1bmRsZVBhdGg6IFwiYXBwL2FwaS9ndy9bLi4ucGF0aF0vcm91dGVcIlxuICAgIH0sXG4gICAgcmVzb2x2ZWRQYWdlUGF0aDogXCIvVXNlcnMvbGlqdW5wZW5nL0Rlc2t0b3Avb3Blbl9zb3VyY2VfcHJvamVjdC9GcmFtZUZsb3cvZnJhbWVmbG93LXdlYi9hcHAvYXBpL2d3L1suLi5wYXRoXS9yb3V0ZS50c1wiLFxuICAgIG5leHRDb25maWdPdXRwdXQsXG4gICAgdXNlcmxhbmRcbn0pO1xuLy8gUHVsbCBvdXQgdGhlIGV4cG9ydHMgdGhhdCB3ZSBuZWVkIHRvIGV4cG9zZSBmcm9tIHRoZSBtb2R1bGUuIFRoaXMgc2hvdWxkXG4vLyBiZSBlbGltaW5hdGVkIHdoZW4gd2UndmUgbW92ZWQgdGhlIG90aGVyIHJvdXRlcyB0byB0aGUgbmV3IGZvcm1hdC4gVGhlc2Vcbi8vIGFyZSB1c2VkIHRvIGhvb2sgaW50byB0aGUgcm91dGUuXG5jb25zdCB7IHdvcmtBc3luY1N0b3JhZ2UsIHdvcmtVbml0QXN5bmNTdG9yYWdlLCBzZXJ2ZXJIb29rcyB9ID0gcm91dGVNb2R1bGU7XG5mdW5jdGlvbiBwYXRjaEZldGNoKCkge1xuICAgIHJldHVybiBfcGF0Y2hGZXRjaCh7XG4gICAgICAgIHdvcmtBc3luY1N0b3JhZ2UsXG4gICAgICAgIHdvcmtVbml0QXN5bmNTdG9yYWdlXG4gICAgfSk7XG59XG5leHBvcnQgeyByb3V0ZU1vZHVsZSwgd29ya0FzeW5jU3RvcmFnZSwgd29ya1VuaXRBc3luY1N0b3JhZ2UsIHNlcnZlckhvb2tzLCBwYXRjaEZldGNoLCAgfTtcblxuLy8jIHNvdXJjZU1hcHBpbmdVUkw9YXBwLXJvdXRlLmpzLm1hcCJdLCJuYW1lcyI6W10sImlnbm9yZUxpc3QiOltdLCJzb3VyY2VSb290IjoiIn0=\n//# sourceURL=webpack-internal:///(rsc)/./node_modules/next/dist/build/webpack/loaders/next-app-loader/index.js?name=app%2Fapi%2Fgw%2F%5B...path%5D%2Froute&page=%2Fapi%2Fgw%2F%5B...path%5D%2Froute&appPaths=&pagePath=private-next-app-dir%2Fapi%2Fgw%2F%5B...path%5D%2Froute.ts&appDir=%2FUsers%2Flijunpeng%2FDesktop%2Fopen_source_project%2FFrameFlow%2Fframeflow-web%2Fapp&pageExtensions=tsx&pageExtensions=ts&pageExtensions=jsx&pageExtensions=js&rootDir=%2FUsers%2Flijunpeng%2FDesktop%2Fopen_source_project%2FFrameFlow%2Fframeflow-web&isDev=true&tsconfigPath=tsconfig.json&basePath=&assetPrefix=&nextConfigOutput=&preferredRegion=&middlewareConfig=e30%3D!\n");

/***/ }),

/***/ "(rsc)/./node_modules/next/dist/build/webpack/loaders/next-flight-client-entry-loader.js?server=true!":
/*!******************************************************************************************************!*\
  !*** ./node_modules/next/dist/build/webpack/loaders/next-flight-client-entry-loader.js?server=true! ***!
  \******************************************************************************************************/
/***/ (() => {



/***/ }),

/***/ "(ssr)/./node_modules/next/dist/build/webpack/loaders/next-flight-client-entry-loader.js?server=true!":
/*!******************************************************************************************************!*\
  !*** ./node_modules/next/dist/build/webpack/loaders/next-flight-client-entry-loader.js?server=true! ***!
  \******************************************************************************************************/
/***/ (() => {



/***/ }),

/***/ "../app-render/after-task-async-storage.external":
/*!***********************************************************************************!*\
  !*** external "next/dist/server/app-render/after-task-async-storage.external.js" ***!
  \***********************************************************************************/
/***/ ((module) => {

"use strict";
module.exports = require("next/dist/server/app-render/after-task-async-storage.external.js");

/***/ }),

/***/ "../app-render/work-async-storage.external":
/*!*****************************************************************************!*\
  !*** external "next/dist/server/app-render/work-async-storage.external.js" ***!
  \*****************************************************************************/
/***/ ((module) => {

"use strict";
module.exports = require("next/dist/server/app-render/work-async-storage.external.js");

/***/ }),

/***/ "./work-unit-async-storage.external":
/*!**********************************************************************************!*\
  !*** external "next/dist/server/app-render/work-unit-async-storage.external.js" ***!
  \**********************************************************************************/
/***/ ((module) => {

"use strict";
module.exports = require("next/dist/server/app-render/work-unit-async-storage.external.js");

/***/ }),

/***/ "next/dist/compiled/next-server/app-page.runtime.dev.js":
/*!*************************************************************************!*\
  !*** external "next/dist/compiled/next-server/app-page.runtime.dev.js" ***!
  \*************************************************************************/
/***/ ((module) => {

"use strict";
module.exports = require("next/dist/compiled/next-server/app-page.runtime.dev.js");

/***/ }),

/***/ "next/dist/compiled/next-server/app-route.runtime.dev.js":
/*!**************************************************************************!*\
  !*** external "next/dist/compiled/next-server/app-route.runtime.dev.js" ***!
  \**************************************************************************/
/***/ ((module) => {

"use strict";
module.exports = require("next/dist/compiled/next-server/app-route.runtime.dev.js");

/***/ })

};
;

// load runtime
var __webpack_require__ = require("../../../../webpack-runtime.js");
__webpack_require__.C(exports);
var __webpack_exec__ = (moduleId) => (__webpack_require__(__webpack_require__.s = moduleId))
var __webpack_exports__ = __webpack_require__.X(0, ["vendor-chunks/next"], () => (__webpack_exec__("(rsc)/./node_modules/next/dist/build/webpack/loaders/next-app-loader/index.js?name=app%2Fapi%2Fgw%2F%5B...path%5D%2Froute&page=%2Fapi%2Fgw%2F%5B...path%5D%2Froute&appPaths=&pagePath=private-next-app-dir%2Fapi%2Fgw%2F%5B...path%5D%2Froute.ts&appDir=%2FUsers%2Flijunpeng%2FDesktop%2Fopen_source_project%2FFrameFlow%2Fframeflow-web%2Fapp&pageExtensions=tsx&pageExtensions=ts&pageExtensions=jsx&pageExtensions=js&rootDir=%2FUsers%2Flijunpeng%2FDesktop%2Fopen_source_project%2FFrameFlow%2Fframeflow-web&isDev=true&tsconfigPath=tsconfig.json&basePath=&assetPrefix=&nextConfigOutput=&preferredRegion=&middlewareConfig=e30%3D!")));
module.exports = __webpack_exports__;

})();