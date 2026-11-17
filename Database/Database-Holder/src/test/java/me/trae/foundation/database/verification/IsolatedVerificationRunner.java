package me.trae.foundation.database.verification;

import java.io.PrintWriter;
import java.lang.reflect.Array;

public final class IsolatedVerificationRunner {

    public static void main(final String[] args) throws ReflectiveOperationException {
        final Class<?> selectorType = Class.forName("org.junit.platform.engine.DiscoverySelector");
        final Class<?> selectorsType = Class.forName("org.junit.platform.engine.discovery.DiscoverySelectors");
        final Object selectorArray = Array.newInstance(selectorType, 2);

        Array.set(selectorArray, 0, selectorsType.getMethod("selectClass", Class.class).invoke(null, IsolatedFixesTest.class));
        Array.set(selectorArray, 1, selectorsType.getMethod("selectClass", Class.class).invoke(null, IsolatedServicesTest.class));

        final Class<?> builderType = Class.forName("org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder");
        final Object builder = builderType.getMethod("request").invoke(null);

        builderType.getMethod("selectors", selectorArray.getClass()).invoke(builder, selectorArray);

        final Object request = builderType.getMethod("build").invoke(builder);
        final Class<?> listenerType = Class.forName("org.junit.platform.launcher.listeners.SummaryGeneratingListener");
        final Object listener = listenerType.getConstructor().newInstance();
        final Class<?> listenerContract = Class.forName("org.junit.platform.launcher.TestExecutionListener");
        final Object listenerArray = Array.newInstance(listenerContract, 1);

        Array.set(listenerArray, 0, listener);

        final Object launcher = Class.forName("org.junit.platform.launcher.core.LauncherFactory").getMethod("create").invoke(null);
        final Class<?> launcherType = Class.forName("org.junit.platform.launcher.Launcher");

        launcherType.getMethod(
                "execute",
                Class.forName("org.junit.platform.launcher.LauncherDiscoveryRequest"),
                listenerArray.getClass()
        ).invoke(launcher, request, listenerArray);

        final Object summary = listenerType.getMethod("getSummary").invoke(listener);
        final Class<?> summaryType = Class.forName("org.junit.platform.launcher.listeners.TestExecutionSummary");
        final PrintWriter printWriter = new PrintWriter(System.out);

        summaryType.getMethod("printTo", PrintWriter.class).invoke(summary, printWriter);
        summaryType.getMethod("printFailuresTo", PrintWriter.class).invoke(summary, printWriter);
        System.out.println("ISOLATED|run=%s|passed=%s|failed=%s|skipped=%s".formatted(
                summaryType.getMethod("getTestsStartedCount").invoke(summary),
                summaryType.getMethod("getTestsSucceededCount").invoke(summary),
                summaryType.getMethod("getTestsFailedCount").invoke(summary),
                summaryType.getMethod("getTestsSkippedCount").invoke(summary)
        ));

        System.exit(Long.class.cast(summaryType.getMethod("getTotalFailureCount").invoke(summary)) == 0L ? 0 : 1);
    }
}