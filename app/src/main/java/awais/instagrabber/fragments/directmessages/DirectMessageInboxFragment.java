package awais.instagrabber.fragments.directmessages;

import android.content.Context;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.List;

import awais.instagrabber.R;
import awais.instagrabber.activities.MainActivity;
import awais.instagrabber.adapters.DirectMessageInboxAdapter;
import awais.instagrabber.broadcasts.DMRefreshBroadcastReceiver;
import awais.instagrabber.customviews.helpers.RecyclerLazyLoaderAtEdge;
import awais.instagrabber.databinding.FragmentDirectMessagesInboxBinding;
import awais.instagrabber.repositories.responses.directmessages.DirectThread;
import awais.instagrabber.viewmodels.DirectInboxViewModel;

public class DirectMessageInboxFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {
    private static final String TAG = "DirectMessagesInboxFrag";

    private CoordinatorLayout root;
    private RecyclerLazyLoaderAtEdge lazyLoader;
    private DirectInboxViewModel viewModel;
    // private boolean refreshInbox = false;
    private boolean shouldRefresh = true;
    private FragmentDirectMessagesInboxBinding binding;
    private DMRefreshBroadcastReceiver receiver;
    private DirectMessageInboxAdapter inboxAdapter;
    private MainActivity fragmentActivity;
    private boolean scrollToTop = false;
    private boolean navigating;
    private Observer<List<DirectThread>> threadsObserver;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fragmentActivity = (MainActivity) getActivity();
        if (fragmentActivity != null) {
            final NavController navController = NavHostFragment.findNavController(this);
            final ViewModelStoreOwner viewModelStoreOwner = navController.getViewModelStoreOwner(R.id.direct_messages_nav_graph);
            viewModel = new ViewModelProvider(viewModelStoreOwner).get(DirectInboxViewModel.class);
        }
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             final ViewGroup container,
                             final Bundle savedInstanceState) {
        if (root != null) {
            shouldRefresh = false;
            return root;
        }
        binding = FragmentDirectMessagesInboxBinding.inflate(inflater, container, false);
        root = binding.getRoot();
        return root;
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        if (!shouldRefresh) return;
        init();
    }

    @Override
    public void onRefresh() {
        lazyLoader.resetState();
        scrollToTop = true;
        if (viewModel != null) {
            viewModel.refresh();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver();
    }

    @Override
    public void onResume() {
        super.onResume();
        observeViewModel();
        final Context context = getContext();
        if (context == null) return;
        receiver = new DMRefreshBroadcastReceiver(() -> {
            Log.d(TAG, "onResume: broadcast received");
            // refreshInbox = true;
        });
        context.registerReceiver(receiver, new IntentFilter(DMRefreshBroadcastReceiver.ACTION_REFRESH_DM));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unregisterReceiver();
    }

    private void unregisterReceiver() {
        if (receiver == null) return;
        final Context context = getContext();
        if (context == null) return;
        context.unregisterReceiver(receiver);
        receiver = null;
    }

    @Override
    public void onConfigurationChanged(@NonNull final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        init();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeViewModelObservers();
        viewModel.onDestroy();
    }

    private void observeViewModel() {
        threadsObserver = list -> {
            if (inboxAdapter == null) return;
            inboxAdapter.submitList(list, () -> {
                if (!scrollToTop) return;
                binding.inboxList.smoothScrollToPosition(0);
                scrollToTop = false;
            });
        };
        viewModel.getThreads().observe(fragmentActivity, threadsObserver);
        viewModel.getFetchingInbox().observe(getViewLifecycleOwner(), fetching -> binding.swipeRefreshLayout.setRefreshing(fetching));
        viewModel.getUnseenCount().observe(getViewLifecycleOwner(), this::setBottomNavBarBadge);
    }

    private void removeViewModelObservers() {
        if (viewModel == null) return;
        if (threadsObserver != null) {
            viewModel.getThreads().removeObserver(threadsObserver);
        }
        // no need to explicitly remove observers whose lifecycle owner is getViewLifecycleOwner
    }

    private void init() {
        final Context context = getContext();
        if (context == null) return;
        observeViewModel();
        binding.swipeRefreshLayout.setOnRefreshListener(this);
        binding.inboxList.setHasFixedSize(true);
        binding.inboxList.setItemViewCacheSize(20);
        final LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        binding.inboxList.setLayoutManager(layoutManager);
        inboxAdapter = new DirectMessageInboxAdapter(thread -> {
            if (navigating) return;
            navigating = true;
            final Bundle bundle = new Bundle();
            bundle.putString("threadId", thread.getThreadId());
            bundle.putString("title", thread.getThreadTitle());
            if (isAdded()) {
                NavHostFragment.findNavController(this).navigate(R.id.action_inbox_to_thread, bundle);
            }
            navigating = false;
        });
        inboxAdapter.setHasStableIds(true);
        binding.inboxList.setAdapter(inboxAdapter);
        lazyLoader = new RecyclerLazyLoaderAtEdge(layoutManager, page -> {
            if (viewModel == null) return;
            viewModel.fetchInbox();
        });
        binding.inboxList.addOnScrollListener(lazyLoader);
    }

    private void setBottomNavBarBadge(final int unseenCount) {
        final BottomNavigationView bottomNavView = fragmentActivity.getBottomNavView();
        final BadgeDrawable badge = bottomNavView.getOrCreateBadge(R.id.direct_messages_nav_graph);
        if (badge == null) return;
        if (unseenCount == 0) {
            badge.setVisible(false);
            badge.clearNumber();
            return;
        }
        if (badge.getVerticalOffset() != 10) {
            badge.setVerticalOffset(10);
        }
        badge.setNumber(unseenCount);
        badge.setVisible(true);
    }
}
