// SIARD KR modifications: foalf0083-ncube (2026). See root LICENSE.txt and NOTICE.md.
package ch.admin.bar.siardsuite.service.database;

import ch.admin.bar.dbexception.DatabaseExceptionHandlerHelper;
import ch.admin.bar.siard2.api.Archive;
import ch.admin.bar.siard2.cmd.MetaDataFromDb;
import ch.admin.bar.siard2.cmd.PrimaryDataFromDb;
import ch.admin.bar.siard2.mysql.expression.MySqlValueExpressionPrimary;
import ch.admin.bar.siardsuite.service.ArchiveHandler;
import ch.admin.bar.siardsuite.service.database.model.LoadDatabaseInstruction;
import ch.admin.bar.siardsuite.service.preferences.UserPreferences;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.util.Pair;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.sql.SQLException;

@Slf4j
public class DatabaseLoadService extends Service<ObservableList<Pair<String, Long>>> {
    private final DatabaseConnectionFactory connectionFactory;
    private final ArchiveHandler archiveHandler;
    private final UserPreferences userPreferences;

    private final LoadDatabaseInstruction instruction;

    public DatabaseLoadService(DatabaseConnectionFactory connectionFactory, ArchiveHandler archiveHandler, UserPreferences userPreferences, LoadDatabaseInstruction instruction) {
        this.connectionFactory = connectionFactory;
        this.archiveHandler = archiveHandler;
        this.userPreferences = userPreferences;
        this.instruction = instruction;

        this.setOnFailed(instruction.getOnFailure());
        this.valueProperty().addListener(instruction.getOnStepCompleted());
        this.progressProperty().addListener(instruction.getOnProgress());
    }

    @Override
    protected Task<ObservableList<Pair<String, Long>>> createTask() {
        return new DatabaseLoadTask(
                connectionFactory,
                archiveHandler,
                userPreferences,
                instruction
        );
    }

    @RequiredArgsConstructor
    private static class DatabaseLoadTask extends Task<ObservableList<Pair<String, Long>>> {

        private final DatabaseConnectionFactory connectionFactory;
        private final ArchiveHandler archiveHandler;
        private final UserPreferences userPreferences;

        private final LoadDatabaseInstruction instruction;

        @Override
        protected ObservableList<Pair<String, Long>> call() throws Exception {
            String databaseProductName = null;
            try {
                val connection = connectionFactory.getOrCreateConnectionProxy(instruction.getConnectionData());
                databaseProductName = connection.getMetaData().getDatabaseProductName();
                val timeout = userPreferences.getStoredOptions().getQueryTimeout();

                Archive archive = instruction.getSaveAt()
                        .map(archiveHandler::init)
                        .orElseGet(archiveHandler::init);

                // sftp & file copy
                archive.setFormDataSet(instruction.getFormDataSet());

                val metaDataFromDb = MetaDataFromDb.newInstance(connection.getMetaData(), archive.getMetaData());
                metaDataFromDb.setQueryTimeout(timeout);
                // 미리보기(저장 위치 없이 메타데이터만)는 고를 목록만 필요하다. 최종 추출은 고른 테이블로 다시 읽는다.
                metaDataFromDb.setPreviewLight(instruction.getLoadOnlyMetadata() && instruction.getSaveAt().isEmpty());

                updateValue(FXCollections.observableArrayList(new Pair<>("Metadata", -1L)));
                updateProgress(0, 100);

                // meta data 조회
                metaDataFromDb.download(
                        instruction.getViewsAsTables(),
                        false,
                        new SiardCmdProgressListener(this::updateProgress),
                        instruction.getSelectedSchemaTableMap()
                );

                instruction.getExternalLobs()
                        .ifPresent(uri -> archiveHandler.setExternalLobFolder(archive, uri));

                ObservableList<Pair<String, Long>> progressData = FXCollections.observableArrayList();
                if (!instruction.getLoadOnlyMetadata()) {

                    PrimaryDataFromDb data = PrimaryDataFromDb.newInstance(connection, archive);

                    data.setQueryTimeout(timeout);
                    updateValue(FXCollections.observableArrayList(new Pair<>("Dataload", -1L)));
                    updateProgress(0, 100);
                    //data.download(new SiardCmdProgressListener(this::updateProgress)); // 읽어들인 데이터 다운로드
                    data.download(new SiardCmdProgressListener((current, total) -> {
                        double progressPercentage = (double) current / total * 100;
                        updateProgress(progressPercentage, 100); // Progress Bar 업데이트
                    })); // 읽어들인 데이터 다운로드

                    // 프로그레스 바
                    archive.getSchemaMap().forEach(
                            (schemaName, schema) -> schema.getSelectedTables().forEach(
                                    table -> {
                                        Pair<String, Long> stringLongPair =
                                                new Pair<>(
                                                        schemaName + "." + table.getMetaTable().getName(),
                                                        table.getMetaTable().getRows()
                                                );
                                        progressData.add(stringLongPair);
                                    }
                            )
                    );
                    updateValue(progressData);
                }

            /*
            Workaround: It seems that the default onSucceed mechanism sometimes is not very stable in java fx 8.
            For that reason, the result is returned with a callback.
             */
                Platform.runLater(() -> instruction.getOnSuccess().accept(archive));

                return progressData;
            } catch (Exception e) {
                if (e instanceof SQLException) {
                    SQLException sqlException = (SQLException) e;
                    DatabaseExceptionHandlerHelper.doHandleSqlException(databaseProductName, null, sqlException);
                }
                throw e;
            }
        }
    }
}