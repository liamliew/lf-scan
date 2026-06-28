package com.lfcreative.lfscan.ui.screen;

import android.content.Context;
import com.lfcreative.lfscan.data.repository.InventoryRepository;
import com.lfcreative.lfscan.session.SessionDataStore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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

  private final Provider<Context> appContextProvider;

  public ScanViewModel_Factory(Provider<InventoryRepository> repositoryProvider,
      Provider<SessionDataStore> sessionDataStoreProvider, Provider<Context> appContextProvider) {
    this.repositoryProvider = repositoryProvider;
    this.sessionDataStoreProvider = sessionDataStoreProvider;
    this.appContextProvider = appContextProvider;
  }

  @Override
  public ScanViewModel get() {
    return newInstance(repositoryProvider.get(), sessionDataStoreProvider.get(), appContextProvider.get());
  }

  public static ScanViewModel_Factory create(Provider<InventoryRepository> repositoryProvider,
      Provider<SessionDataStore> sessionDataStoreProvider, Provider<Context> appContextProvider) {
    return new ScanViewModel_Factory(repositoryProvider, sessionDataStoreProvider, appContextProvider);
  }

  public static ScanViewModel newInstance(InventoryRepository repository,
      SessionDataStore sessionDataStore, Context appContext) {
    return new ScanViewModel(repository, sessionDataStore, appContext);
  }
}
