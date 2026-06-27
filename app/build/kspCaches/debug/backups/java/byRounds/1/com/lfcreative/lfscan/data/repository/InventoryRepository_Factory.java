package com.lfcreative.lfscan.data.repository;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
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
public final class InventoryRepository_Factory implements Factory<InventoryRepository> {
  @Override
  public InventoryRepository get() {
    return newInstance();
  }

  public static InventoryRepository_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static InventoryRepository newInstance() {
    return new InventoryRepository();
  }

  private static final class InstanceHolder {
    private static final InventoryRepository_Factory INSTANCE = new InventoryRepository_Factory();
  }
}
