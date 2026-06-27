package com.lfcreative.lfscan.ui.screen;

import com.lfcreative.lfscan.data.repository.InventoryRepository;
import com.lfcreative.lfscan.session.SessionDataStore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class ScanViewModel_Factory implements Factory<ScanViewModel> {
  private final Provider<InventoryRepository> repositoryProvider;

  private final Provider<SessionDataStore> sessionDataStoreProvider;

  public ScanViewModel_Factory(Provider<InventoryRepository> repositoryProvider,
      Provider<SessionDataStore> sessionDataStoreProvider) {
    this.repositoryProvider = repositoryProvider;
    this.sessionDataStoreProvider = sessionDataStoreProvider;
  }

  @Override
  public ScanViewModel get() {
    return newInstance(repositoryProvider.get(), sessionDataStoreProvider.get());
  }

  public static ScanViewModel_Factory create(Provider<InventoryRepository> repositoryProvider,
      Provider<SessionDataStore> sessionDataStoreProvider) {
    return new ScanViewModel_Factory(repositoryProvider, sessionDataStoreProvider);
  }

  public static ScanViewModel newInstance(InventoryRepository repository,
      SessionDataStore sessionDataStore) {
    return new ScanViewModel(repository, sessionDataStore);
  }
}
