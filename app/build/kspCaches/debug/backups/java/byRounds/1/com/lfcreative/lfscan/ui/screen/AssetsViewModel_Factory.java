package com.lfcreative.lfscan.ui.screen;

import com.lfcreative.lfscan.data.repository.InventoryRepository;
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
public final class AssetsViewModel_Factory implements Factory<AssetsViewModel> {
  private final Provider<InventoryRepository> repositoryProvider;

  public AssetsViewModel_Factory(Provider<InventoryRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public AssetsViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static AssetsViewModel_Factory create(Provider<InventoryRepository> repositoryProvider) {
    return new AssetsViewModel_Factory(repositoryProvider);
  }

  public static AssetsViewModel newInstance(InventoryRepository repository) {
    return new AssetsViewModel(repository);
  }
}
