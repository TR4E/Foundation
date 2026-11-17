package me.trae.foundation.database.deep;

import me.trae.foundation.database.postgres.driver.PostgresDriver;
import me.trae.foundation.database.postgres.driver.PostgresSettings;
import me.trae.foundation.database.postgres.schema.PostgresSynchronizer;
import me.trae.foundation.database.postgres.schema.SchemaDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DdlBootProcess {

    public static void main(final String[] argumentArray) throws Exception {
        final PostgresDriver postgresDriver = new PostgresDriver(new PostgresSettings(argumentArray[0], Integer.parseInt(argumentArray[1]), argumentArray[2], "deep", "deep", 2));

        postgresDriver.connect();

        try {
            final List<String> statementList = new ArrayList<>();

            statementList.add("SELECT pg_sleep(0.8)");
            statementList.addAll(SchemaDefinition.statements("simultaneous_boot", false, List.of(), Map.of()));
            System.out.println("READY");
            System.out.flush();
            System.in.read();
            new PostgresSynchronizer(postgresDriver).synchronize(statementList);
            System.out.println("DONE");
        } finally {
            postgresDriver.disconnect();
        }
    }
}