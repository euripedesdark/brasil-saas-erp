import 'webpack-entry';
import { PluginManifest, PluginStore } from 'graylog-web-plugin/plugin';
import metadata from '../../package.json';
import BrasilSaasBi from './BrasilSaasBi';

PluginStore.register(new PluginManifest(metadata, {
    routes: [{ path: '/brasil-saas-bi', component: BrasilSaasBi }],
    navigation: [{ path: '/brasil-saas-bi', description: 'Brasil SaaS BI' }],
}));
