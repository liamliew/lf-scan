package com.lfcreative.lfscan.session;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
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
public final class SessionDataStore_Factory implements Factory<SessionDataStore> {
  private final Provider<Context> contextProvider;

  public SessionDataStore_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public SessionDataStore get() {
    return newInstance(contextProvider.get());
  }

  public static SessionDataStore_Factory create(Provider<Context> contextProvider) {
    return new SessionDataStore_Factory(contextProvider);
  }

  public static SessionDataStore newInstance(Context context) {
    return new SessionDataStore(context);
  }
}
