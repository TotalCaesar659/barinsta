package awais.instagrabber.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.controller.BaseControllerListener;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.imagepipeline.image.ImageInfo;

import java.io.File;

import awais.instagrabber.R;
import awais.instagrabber.asyncs.ProfilePictureFetcher;
import awais.instagrabber.databinding.DialogProfilepicBinding;
import awais.instagrabber.db.entities.Account;
import awais.instagrabber.db.repositories.RepositoryCallback;
import awais.instagrabber.interfaces.FetchListener;
import awais.instagrabber.models.StoryModel;
import awais.instagrabber.repositories.responses.UserInfo;
import awais.instagrabber.utils.Constants;
import awais.instagrabber.utils.CookieUtils;
import awais.instagrabber.utils.DownloadUtils;
import awais.instagrabber.utils.TextUtils;
import awais.instagrabber.webservices.ProfileService;
import awais.instagrabber.webservices.ServiceCallback;
import awais.instagrabber.webservices.StoriesService;

import static awais.instagrabber.utils.Utils.settingsHelper;

public class ProfilePicDialogFragment extends DialogFragment {
    private static final String TAG = "ProfilePicDlgFragment";

    private final String id;
    private final String name;
    private final String fallbackUrl;

    private boolean isLoggedIn;
    private DialogProfilepicBinding binding;
    private String url;

    private final FetchListener<String> fetchListener = profileUrl -> {
        url = profileUrl;
        setupPhoto();
    };

    public ProfilePicDialogFragment(final String id, final String name, final String fallbackUrl) {
        this.id = id;
        this.name = name;
        this.fallbackUrl = fallbackUrl;
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             final ViewGroup container,
                             final Bundle savedInstanceState) {
        binding = DialogProfilepicBinding.inflate(inflater, container, false);
        final String cookie = settingsHelper.getString(Constants.COOKIE);
        isLoggedIn = !TextUtils.isEmpty(cookie) && CookieUtils.getUserIdFromCookie(cookie) != null;
        return binding.getRoot();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        final Dialog dialog = getDialog();
        if (dialog == null) return;
        final Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        int width = ViewGroup.LayoutParams.MATCH_PARENT;
        int height = ViewGroup.LayoutParams.MATCH_PARENT;
        window.setLayout(width, height);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        init();
        if (id.contains("_")) fetchStory();
        else fetchAvatar();
    }

    private void init() {
        binding.download.setOnClickListener(v -> {
            final Context context = getContext();
            if (context == null) return;
            if (ContextCompat.checkSelfPermission(context, DownloadUtils.PERMS[0]) == PackageManager.PERMISSION_GRANTED) {
                downloadProfilePicture();
                return;
            }
            requestPermissions(DownloadUtils.PERMS, 8020);
        });
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 8020 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            downloadProfilePicture();
        }
    }

    private void fetchAvatar() {
        if (isLoggedIn) {
            final ProfileService profileService = ProfileService.getInstance();
            profileService.getUserInfo(id, new ServiceCallback<UserInfo>() {
                @Override
                public void onSuccess(final UserInfo result) {
                    if (result != null) {
                        fetchListener.onResult(result.getHDProfilePicUrl());
                    }
                }

                @Override
                public void onFailure(final Throwable t) {
                    final Context context = getContext();
                    Toast.makeText(context, R.string.downloader_unknown_error, Toast.LENGTH_SHORT).show();
                    getDialog().dismiss();
                }
            });
        }
        else new ProfilePictureFetcher(name, id, fetchListener, fallbackUrl, false).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void fetchStory() {
        final StoriesService storiesService = StoriesService.getInstance();
        storiesService.fetch(id, new ServiceCallback<StoryModel>() {
            @Override
            public void onSuccess(final StoryModel result) {
                if (result != null) {
                    fetchListener.onResult(result.getStoryUrl());
                }
            }

            @Override
            public void onFailure(final Throwable t) {
                final Context context = getContext();
                Log.d("austin_debug", "error", t);
                Toast.makeText(context, R.string.downloader_unknown_error, Toast.LENGTH_SHORT).show();
                getDialog().dismiss();
            }
        });
    }

    private void setupPhoto() {
        if (TextUtils.isEmpty(url)) {
            url = fallbackUrl;
        }
        final DraweeController controller = Fresco
                .newDraweeControllerBuilder()
                .setUri(url)
                .setOldController(binding.imageViewer.getController())
                .setControllerListener(new BaseControllerListener<ImageInfo>() {
                    @Override
                    public void onFailure(final String id, final Throwable throwable) {
                        super.onFailure(id, throwable);
                        binding.download.setVisibility(View.GONE);
                        binding.progressView.setVisibility(View.GONE);
                    }

                    @Override
                    public void onFinalImageSet(final String id,
                                                final ImageInfo imageInfo,
                                                final Animatable animatable) {
                        super.onFinalImageSet(id, imageInfo, animatable);
                        binding.download.setVisibility(View.VISIBLE);
                        binding.progressView.setVisibility(View.GONE);
                    }
                })
                .build();
        binding.imageViewer.setController(controller);
    }

    private void downloadProfilePicture() {
        if (url == null) return;
        final File dir = new File(Environment.getExternalStorageDirectory(), "Download");
        final Context context = getContext();
        if (context == null) return;
        if (dir.exists() || dir.mkdirs()) {
            final File saveFile = new File(dir, name + '_' + System.currentTimeMillis() + ".jpg");
            DownloadUtils.download(context, url, saveFile.getAbsolutePath());
            return;
        }
        Toast.makeText(context, R.string.downloader_error_creating_folder, Toast.LENGTH_SHORT).show();
    }
}
