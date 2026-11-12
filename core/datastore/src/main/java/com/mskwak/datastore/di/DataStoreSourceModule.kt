package com.mskwak.datastore.di

import com.mskwak.datastore.UserPreferencesLocalSource
import com.mskwak.datastore.source_impl.UserPreferencesLocalSourceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataStoreSourceModule {
    @Singleton
    @Binds
    abstract fun bindUserPreferencesLocalSource(
        impl: UserPreferencesLocalSourceImpl
    ): UserPreferencesLocalSource
}
