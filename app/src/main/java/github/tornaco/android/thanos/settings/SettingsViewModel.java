package github.tornaco.android.thanos.settings;

import android.app.Application;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.lifecycle.AndroidViewModel;

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import github.tornaco.android.thanos.core.app.ThanosManager;
import github.tornaco.android.thanos.core.backup.IBackupCallback;
import github.tornaco.android.thanos.core.backup.IFileDescriptorConsumer;
import github.tornaco.android.thanos.core.backup.IFileDescriptorInitializer;
import github.tornaco.android.thanos.core.util.DevNull;
import github.tornaco.android.thanos.core.util.FileUtils;
import github.tornaco.android.thanos.core.util.Timber;
import io.reactivex.Observable;
import rx2.android.schedulers.AndroidSchedulers;
import util.IoUtils;

public class SettingsViewModel extends AndroidViewModel {

    public SettingsViewModel(@NonNull Application application) {
        super(application);
    }

    @SuppressWarnings("UnstableApiUsage")
    void performRestore(RestoreListener listener, Uri uri) {
        ThanosManager.from(getApplication())
                .ifServiceInstalled(thanosManager -> {
                    File tmpDir = new File(getApplication().getCacheDir(), "restore_tmp");

                    try {
                        File tmpZipFile = new File(tmpDir,
                                String.format("tem_restore_%s.zip", System.currentTimeMillis()));
                        Files.createParentDirs(tmpZipFile);

                        InputStream inputStream = getApplication()
                                .getContentResolver()
                                .openInputStream(uri);

                        if (inputStream == null) {
                            listener.onFail("Input stream is null..." + uri);
                            return;
                        }

                        byte[] buffer = new byte[inputStream.available()];
                        inputStream.read(buffer);
                        Files.write(buffer, tmpZipFile);

                        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(tmpZipFile, ParcelFileDescriptor.MODE_READ_ONLY);
                        thanosManager.getBackupAgent().performRestore(pfd, null, null, new IBackupCallback.Stub() {

                            @Override
                            public void onBackupFinished(String domain, String path) {
                                // Not for us.
                            }

                            @Override
                            public void onRestoreFinished(String domain, String path) {
                                listener.onSuccess();
                                Timber.d("onRestoreFinished: " + path);
                            }

                            @Override
                            public void onFail(String message) {
                                listener.onFail(message);
                                Timber.d("onFail: " + message);
                            }

                            @Override
                            public void onProgress(String progressMessage) {

                            }
                        });
                    } catch (Exception e) {
                        listener.onFail(Log.getStackTraceString(e));
                    } finally {
                        FileUtils.deleteDirQuiet(tmpDir);
                    }
                });
    }

    @SuppressWarnings("UnstableApiUsage")
    void performBackup(BackupListener listener, OutputStream externalBackupDirOs) {
        File backupDir = new File(getApplication().getCacheDir(), "backup");
        // File externalBackupDir = new File(getApplication().getExternalCacheDir(), "backup");
        ThanosManager.from(getApplication())
                .ifServiceInstalled(thanosManager -> thanosManager.getBackupAgent()
                        .performBackup(
                                new IFileDescriptorInitializer.Stub() {
                                    @Override
                                    public void initParcelFileDescriptor(String domain, String path,
                                                                         IFileDescriptorConsumer consumer)
                                            throws RemoteException {
                                        File subFile = new File(backupDir, path);
                                        Timber.d("create sub file: " + subFile);
                                        try {
                                            Files.createParentDirs(subFile);
                                            if (subFile.createNewFile()) {
                                                ParcelFileDescriptor pfd = ParcelFileDescriptor.open(subFile, ParcelFileDescriptor.MODE_READ_WRITE);
                                                consumer.acceptAppParcelFileDescriptor(pfd);
                                            } else {
                                                consumer.acceptAppParcelFileDescriptor(null);
                                            }
                                        } catch (IOException e) {
                                            Timber.e("createParentDirs fail: " + Log.getStackTraceString(e));
                                            consumer.acceptAppParcelFileDescriptor(null);
                                        }
                                    }
                                },
                                null,
                                null,
                                new IBackupCallback.Stub() {
                                    @Override
                                    public void onBackupFinished(String domain, String path) {
                                        Timber.d("onBackupFinished: " + path);
                                        File subFile = new File(backupDir, path);
                                        // Move it to dest.
                                        try {
                                            ByteStreams.copy(Files.asByteSource(subFile).openStream(), externalBackupDirOs);
                                            DevNull.accept(Observable.just("Success...")
                                                    .observeOn(AndroidSchedulers.mainThread())
                                                    .subscribe(o -> listener.onSuccess()));
                                        } catch (Throwable e) {
                                            DevNull.accept(Observable.just(backupDir)
                                                    .observeOn(AndroidSchedulers.mainThread())
                                                    .subscribe(file -> listener.onFail(e.getLocalizedMessage())));
                                            Timber.e("move fail: " + Log.getStackTraceString(e));
                                        } finally {
                                            FileUtils.deleteDirQuiet(backupDir);
                                            Timber.d("deleteDirQuiet cleanup: " + backupDir);
                                            IoUtils.closeQuietly(externalBackupDirOs);
                                        }
                                    }

                                    @Override
                                    public void onRestoreFinished(String domain, String path) {

                                    }

                                    @Override
                                    public void onFail(String message) {
                                        DevNull.accept(Observable.just(backupDir)
                                                .observeOn(AndroidSchedulers.mainThread())
                                                .subscribe(file -> listener.onFail(message)));
                                    }

                                    @Override
                                    public void onProgress(String progressMessage) {

                                    }
                                }));
    }

    interface BackupListener {
        @UiThread
        void onSuccess();

        @UiThread
        void onFail(String errMsg);
    }

    interface RestoreListener {
        @UiThread
        void onSuccess();

        @UiThread
        void onFail(String errMsg);
    }
}
