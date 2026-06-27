package com.lfcreative.lfscan.ui.screen;

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
public final class ModeSelectViewModel_Factory implements Factory<ModeSelectViewModel> {
  private final Provider<SessionDataStore> sessionDataStoreProvider;

  public ModeSelectViewModel_Factory(Provider<SessionDataStore> sessionDataStoreProvider) {
    this.sessionDataStoreProvider = sessionDataStoreProvider;
  }

  @Override
  public ModeSelectViewModel get() {
    return newInstance(sessionDataStoreProvider.get());
  }

  public static ModeSelectViewModel_Factory create(
      Provider<SessionDataStore> sessionDataStoreProvider) {
    return new ModeSelectViewModel_Factory(sessionDataStoreProvider);
  }

  public static ModeSelectViewModel newInstance(SessionDataStore sessionDataStore) {
    return new ModeSelectViewModel(sessionDataStore);
  }
}
