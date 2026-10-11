package br.com.brasil_saas.graylog;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.graylog2.plugin.Plugin;
import org.graylog2.plugin.PluginMetaData;
import org.graylog2.plugin.PluginModule;
import org.graylog2.plugin.ServerStatus;
import org.graylog2.plugin.Version;

/** Presentation only: no database connection or shared service credentials. */
public final class BrasilSaasBiPlugin implements Plugin {
    @Override public Collection<PluginModule> modules() { return Collections.emptyList(); }
    @Override public PluginMetaData metadata() { return new Metadata(); }

    private static final class Metadata implements PluginMetaData {
        @Override public String getUniqueId() { return BrasilSaasBiPlugin.class.getName(); }
        @Override public String getName() { return "Brasil SaaS BI"; }
        @Override public String getAuthor() { return "Brasil SaaS ERP"; }
        @Override public URI getURL() { return URI.create("https://github.com/euripedesdark/brasil-saas-erp"); }
        @Override public Version getVersion() { return Version.from(1, 0, 0); }
        @Override public String getDescription() { return "Consultas seletivas do BI com autenticacao e empresa controladas pelo ERP"; }
        @Override public Version getRequiredVersion() { return Version.from(7, 1, 9); }
        @Override public Set<ServerStatus.Capability> getRequiredCapabilities() { return Collections.emptySet(); }
    }
}
