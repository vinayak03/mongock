package com.github.cloudyrock.mongock.driver.mongodb.sync.v4.changelogs;

import com.github.cloudyrock.mongock.migration.MongockLegacyMigration;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.changock.driver.api.entry.ChangeEntry;
import io.changock.driver.api.entry.ChangeEntryService;
import io.changock.driver.api.entry.ChangeState;
import io.changock.migration.api.annotations.NonLockGuarded;
import io.changock.migration.api.annotations.NonLockGuardedType;
import io.changock.migration.api.exception.ChangockException;
import io.changock.runner.core.builder.configuration.LegacyMigrationMappingFields;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;


public class LegacyService {

  private final static Logger logger = LoggerFactory.getLogger(LegacyService.class);

  public void executeMigration(@NonLockGuarded(NonLockGuardedType.NONE)
                                           @Named("legacy-migration") MongockLegacyMigration legacyMigration,
                                           MongoDatabase mongoDatabase,
                                           ChangeEntryService<ChangeEntry> changeEntryService) {
    int changesMigrated = 0;
    Integer changesCountExpectation = legacyMigration.getChangesCountExpectation();
    if(changesCountExpectation == null) {
      logger.warn("[legacy-migration] - There is no changes count expectation!");
    }
    try {
      validateLegacyMigration(legacyMigration);
      List<ChangeEntry> changesToMigrate = getOriginalMigrationAsChangeEntryList(mongoDatabase.getCollection(legacyMigration.getCollectionName()), legacyMigration);
      for (ChangeEntry originalChange : changesToMigrate) {
        if (!changeEntryService.isAlreadyExecuted(originalChange.getChangeId(), originalChange.getAuthor())) {
          logTracking(originalChange);
          changeEntryService.save(originalChange);
          logSuccessfullyTracked(originalChange);
        } else {
          logAlreadyTracked(originalChange);
        }
        changesMigrated++;
      }
      if(changesCountExpectation != null && changesCountExpectation != changesMigrated) {
        throw new ChangockException(String.format("[legacy-migration] - Expectation [%d changes migrated], but actual [%d changes migrated]", changesCountExpectation, changesMigrated));
      }
      logger.debug("[legacy-migration] - {} changes migrated", changesMigrated);
    } catch (Exception ex) {
      if (legacyMigration.isFailFast()) {
        throw ex instanceof ChangockException ? (ChangockException) ex : new ChangockException(ex);
      }
      logger.warn(ex.getMessage());
    }

  }

  private List<ChangeEntry> getOriginalMigrationAsChangeEntryList(MongoCollection<Document> originalCollection, MongockLegacyMigration legacyMigration) {

    List<ChangeEntry> originalMigrations = new ArrayList<>();
    LegacyMigrationMappingFields mappingFields = legacyMigration.getMappingFields();

    FindIterable<Document> docs = originalCollection.find();
    Iterator<Document> iteratorOriginalMigration = docs.iterator();
    String executionId = getExecutionId();
    while (iteratorOriginalMigration.hasNext()) {
      Document changeDocument = iteratorOriginalMigration.next();
      ChangeEntry change = new ChangeEntry(
          executionId,
          getDocumentStringValue(changeDocument, mappingFields.getChangeId()),
          getDocumentStringValue(changeDocument, mappingFields.getAuthor()),
          getDocumentDateValue(changeDocument, mappingFields.getTimestamp()),
          ChangeState.EXECUTED,
          getDocumentStringValue(changeDocument, mappingFields.getChangeLogClass()),
          getDocumentStringValue(changeDocument, mappingFields.getChangeSetMethod()),
          -1L,
          getMetadata(changeDocument, mappingFields.getMetadata())
      );
      originalMigrations.add(change);
    }
    return originalMigrations;
  }

  private Object getMetadata(Document changeDocument, String field) {
    Map<String, Object> newMetadata = new HashMap<>();
    newMetadata.put("migration-type", "legacy");
    Object originalMetadata;
    if ((originalMetadata = field != null ? changeDocument.getString(field) : null) != null) {
      newMetadata.put("original-metadata", originalMetadata);
    }
    return newMetadata;
  }

  private String getDocumentStringValue(Document changeDocument, String field) {
    return field != null ? changeDocument.getString(field) : null;
  }

  private Date getDocumentDateValue(Document changeDocument, String field) {
    return field != null ? changeDocument.getDate(field) : null;
  }

  private String getExecutionId() {
    return String.format("%s-%s-%d", "legacy_migration", LocalDateTime.now().toString(), new Random().nextInt(999));
  }

  private void validateLegacyMigration(MongockLegacyMigration legacyMigration) {
    if (legacyMigration == null
        || isEmpty(legacyMigration.getCollectionName())
        || legacyMigration.getMappingFields() == null
        || isEmpty(legacyMigration.getMappingFields().getChangeId())
        || isEmpty(legacyMigration.getMappingFields().getAuthor())) {
      throw new ChangockException("[legacy-migration] - wrong configured. Either is null, or doesn't contain collectionName or mapping fields are wrong");
    }
  }

  private static boolean isEmpty(String text) {
    return text == null || text.isEmpty();
  }

  private void logAlreadyTracked(ChangeEntry originalChange) {
    logger.debug("[legacy-migration] - Change[changeId: {} ][author: {} ] already tracked in Mongock changeLog collection", originalChange.getChangeId(), originalChange.getAuthor());
  }

  private void logSuccessfullyTracked(ChangeEntry originalChange) {
    logger.debug("[legacy-migration] - Change[changeId: {} ][author: {} ] tracked successfully", originalChange.getChangeId(), originalChange.getAuthor());
  }

  private void logTracking(ChangeEntry originalChange) {
    logger.debug("[legacy-migration] - Tracking change[changeId: {} ][author: {} ]...", originalChange.getChangeId(), originalChange.getAuthor());
  }
}
