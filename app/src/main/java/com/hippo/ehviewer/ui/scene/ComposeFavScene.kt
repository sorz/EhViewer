package com.hippo.ehviewer.ui.scene

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.data.FavListUrlBuilder
import com.hippo.ehviewer.client.data.FavListUrlBuilder.Companion.FAV_CAT_LOCAL
import com.hippo.ehviewer.client.data.GalleryInfo
import com.hippo.ehviewer.ui.main.GalleryInfoListItem
import com.hippo.ehviewer.ui.setMD3Content
import eu.kanade.tachiyomi.util.system.pxToDp

class ComposeFavScene : BaseScene() {
    private var curFav by mutableIntStateOf(Settings.recentFavCat)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = ComposeView(inflater.context).apply {
        setMD3Content {
            Scaffold(
                topBar = {
                    var active by remember { mutableStateOf(false) }
                    val favCatName: String = when (curFav) {
                        in 0..9 -> Settings.favCat[curFav]
                        FAV_CAT_LOCAL -> stringResource(id = R.string.local_favorites)
                        else -> stringResource(id = R.string.cloud_favorites)
                    }
                    SearchBar(
                        query = favCatName,
                        onQueryChange = { },
                        onSearch = { },
                        active = active,
                        onActiveChange = { active = it },
                        leadingIcon = {
                            IconButton(onClick = { toggleDrawer(GravityCompat.START) }) {
                                Icon(imageVector = Icons.Default.Menu, contentDescription = null)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 18.dp),
                    ) {
                    }
                },
            ) {
                val favBuilder = rememberSaveable(curFav) { FavListUrlBuilder(favCat = curFav) }
                val localFavData = remember {
                    Pager(PagingConfig(25)) {
                        EhDB.localFavLazyList
                    }.flow.cachedIn(lifecycleScope)
                }.collectAsLazyPagingItems()
                val cloudData = remember(curFav) {
                    Pager(PagingConfig(25)) {
                        object : PagingSource<String, GalleryInfo>() {
                            override fun getRefreshKey(state: PagingState<String, GalleryInfo>): String? = null
                            override suspend fun load(params: LoadParams<String>): LoadResult<String, GalleryInfo> {
                                when (params) {
                                    is LoadParams.Prepend -> {
                                        favBuilder.setIndex(params.key, isNext = false)
                                    }
                                    is LoadParams.Append -> {
                                        favBuilder.setIndex(params.key, isNext = true)
                                    }
                                    is LoadParams.Refresh -> {
                                    }
                                }
                                val r = EhEngine.getFavorites(favBuilder.build())
                                Settings.favCat = r.catArray
                                Settings.favCount = r.countArray
                                Settings.favCloudCount = r.countArray.sum()
                                return LoadResult.Page(r.galleryInfoList, r.prev, r.next)
                            }
                        }
                    }.flow.cachedIn(lifecycleScope)
                }.collectAsLazyPagingItems()
                val data = if (curFav == FAV_CAT_LOCAL) localFavData else cloudData
                val height = remember { (3 * Settings.listThumbSize * 3).pxToDp.dp }
                LazyColumn(
                    modifier = Modifier.padding(horizontal = dimensionResource(id = R.dimen.gallery_list_margin_h), vertical = dimensionResource(id = R.dimen.gallery_list_margin_v)),
                    verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.gallery_list_interval)),
                    contentPadding = it,
                ) {
                    if (data.itemCount == 0) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    items(
                        count = data.itemCount,
                        key = data.itemKey(key = { item -> item.gid }),
                        contentType = data.itemContentType(),
                    ) { index ->
                        val item = data[index]
                        if (item != null) {
                            GalleryInfoListItem(
                                onClick = {
                                    navAnimated(
                                        R.id.galleryDetailScene,
                                        bundleOf(
                                            GalleryDetailScene.KEY_ACTION to GalleryDetailScene.ACTION_GALLERY_INFO,
                                            GalleryDetailScene.KEY_GALLERY_INFO to item,
                                        ),
                                    )
                                },
                                onLongClick = {},
                                info = item,
                                modifier = Modifier.height(height),
                                isInFavScene = true,
                                showPages = Settings.showGalleryPages,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onCreateDrawerView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(inflater.context).apply {
            setMD3Content {
                ElevatedCard {
                    val scope = currentRecomposeScope
                    LaunchedEffect(Unit) {
                        Settings.favChangesFlow.collect {
                            scope.invalidate()
                        }
                    }
                    val faves = arrayOf(
                        stringResource(id = R.string.local_favorites) to Settings.favLocalCount,
                        stringResource(id = R.string.cloud_favorites) to Settings.favCloudCount,
                        *Settings.favCat.zip(Settings.favCount.toTypedArray()).toTypedArray(),
                    )
                    TopAppBar(title = { Text(text = stringResource(id = R.string.collections)) })
                    LazyColumn {
                        itemsIndexed(faves) { index, (name, count) ->
                            ListItem(
                                headlineContent = { Text(text = name) },
                                trailingContent = { Text(text = count.toString(), style = MaterialTheme.typography.bodyLarge) },
                                modifier = Modifier.clickable {
                                    curFav = index - 2
                                    toggleDrawer(GravityCompat.END)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
