const path = require('node:path');
const web = process.env.GRAYLOG_WEB_SRC;
if (!web || !path.isAbsolute(web)) throw new Error('GRAYLOG_WEB_SRC must be the absolute path to the Graylog 7.1.9 web sources');
const { PluginWebpackConfig } = require(path.join(web, 'packages/graylog-web-plugin'));
module.exports = new PluginWebpackConfig(__dirname, 'br.com.brasil_saas.graylog.BrasilSaasBiPlugin', { web_src_path: web }, {
    resolve: { alias: { react: path.join(web, 'node_modules/react') } },
});
