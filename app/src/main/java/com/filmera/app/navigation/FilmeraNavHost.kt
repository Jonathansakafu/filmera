package com.filmera.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.filmera.app.container
import com.filmera.app.core.filter.FilterCategory
import com.filmera.app.feature.camera.CameraScreen
import com.filmera.app.feature.compatibility.CompatibilityScreen
import com.filmera.app.feature.gallery.GalleryScreen
import com.filmera.app.feature.gallery.ViewerScreen
import com.filmera.app.feature.home.HomeScreen
import com.filmera.app.feature.onboarding.IntroScreen
import com.filmera.app.feature.onboarding.OnboardingScreen
import com.filmera.app.feature.settings.PrivacyScreen
import com.filmera.app.feature.settings.SettingsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private object Routes {
    const val INTRO = "intro"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CAMERA = "camera?category={category}&filter={filter}"
    const val GALLERY = "gallery"
    const val VIEWER = "viewer/{name}"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    const val COMPATIBILITY = "compatibility"

    fun camera(category: FilterCategory?, filter: String?) =
        "camera?category=${category?.name ?: ""}&filter=${filter ?: ""}"

    fun viewer(name: String) = "viewer/$name"
}

@Composable
fun FilmeraNavHost(nav: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    NavHost(
        navController = nav,
        startDestination = Routes.INTRO,
        enterTransition = { slideInHorizontally(tween(280)) { it / 5 } + fadeIn(tween(280)) },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { slideOutHorizontally(tween(260)) { it / 5 } + fadeOut(tween(220)) },
    ) {
        composable(Routes.INTRO, enterTransition = { fadeIn() }) {
            IntroScreen(onFinished = {
                scope.launch {
                    val done = context.container.settings.settings.first().onboardingDone
                    nav.navigate(if (done) Routes.HOME else Routes.ONBOARDING) {
                        popUpTo(Routes.INTRO) { inclusive = true }
                    }
                }
            })
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onDone = {
                scope.launch { context.container.settings.setOnboardingDone() }
                nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }
        composable(Routes.HOME) {
            HomeScreen(
                onOpenCamera = { cat, filter -> nav.navigate(Routes.camera(cat, filter)) },
                onOpenGallery = { nav.navigate(Routes.GALLERY) },
                onOpenCapture = { nav.navigate(Routes.viewer(it)) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenCompatibility = { nav.navigate(Routes.COMPATIBILITY) },
            )
        }
        composable(
            Routes.CAMERA,
            arguments = listOf(
                navArgument("category") { type = NavType.StringType; defaultValue = "" },
                navArgument("filter") { type = NavType.StringType; defaultValue = "" },
            ),
            enterTransition = { fadeIn(tween(250)) },
            popExitTransition = { fadeOut(tween(200)) },
        ) { entry ->
            val category = entry.arguments?.getString("category")
                ?.takeIf { it.isNotEmpty() }
                ?.let { runCatching { FilterCategory.valueOf(it) }.getOrNull() }
            val filter = entry.arguments?.getString("filter")?.takeIf { it.isNotEmpty() }
            CameraScreen(
                initialCategory = category,
                initialFilterId = filter,
                onBack = { nav.popBackStack() },
                onOpenGallery = { nav.navigate(Routes.GALLERY) },
                onOpenCapture = { nav.navigate(Routes.viewer(it)) },
            )
        }
        composable(Routes.GALLERY) {
            GalleryScreen(
                onBack = { nav.popBackStack() },
                onOpenCamera = {
                    if (!nav.popBackStack(Routes.CAMERA, inclusive = false)) nav.navigate(Routes.camera(null, null))
                },
                onOpenCapture = { nav.navigate(Routes.viewer(it)) },
            )
        }
        composable(Routes.VIEWER, arguments = listOf(navArgument("name") { type = NavType.StringType })) { entry ->
            ViewerScreen(name = entry.arguments?.getString("name").orEmpty(), onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onOpenCompatibility = { nav.navigate(Routes.COMPATIBILITY) },
                onOpenPrivacy = { nav.navigate(Routes.PRIVACY) },
            )
        }
        composable(Routes.PRIVACY) { PrivacyScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.COMPATIBILITY) { CompatibilityScreen(onBack = { nav.popBackStack() }) }
    }
}
