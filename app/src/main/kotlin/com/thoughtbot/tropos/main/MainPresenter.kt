package com.thoughtbot.tropos.main

import com.thoughtbot.tropos.R
import com.thoughtbot.tropos.commons.Presenter
import com.thoughtbot.tropos.data.LocationDataSource
import com.thoughtbot.tropos.data.WeatherDataSource
import com.thoughtbot.tropos.data.remote.LocationService
import com.thoughtbot.tropos.data.remote.WeatherDataService
import com.thoughtbot.tropos.extensions.dayBefore
import com.thoughtbot.tropos.permissions.LocationPermission
import com.thoughtbot.tropos.permissions.Permission
import com.thoughtbot.tropos.permissions.PermissionResults
import com.thoughtbot.tropos.permissions.checkPermission
import com.thoughtbot.tropos.refresh.PullToRefreshLayout.RefreshListener
import io.reactivex.disposables.Disposable
import java.util.Date

class MainPresenter(override val view: MainView,
    val locationDataSource: LocationDataSource = LocationService(view.context),
    val weatherDataSource: WeatherDataSource = WeatherDataService(),
    val permission: Permission = LocationPermission(
        view.context)) : Presenter, RefreshListener, PermissionResults {

  lateinit var disposable: Disposable

  fun init() {
    permission.checkPermission({ updateWeather() }, { onPermissionDenied(false) }, true)
  }

  fun updateWeather() {
    //TODO confirm observable is completing
    view.viewState = ViewState.Loading(ToolbarViewModel(view.context, null))
    disposable = locationDataSource.fetchLocation()
        .flatMap { weatherDataSource.fetchForecast(it, 3) }
        .flatMap({ forecast ->
          weatherDataSource.fetchWeather(forecast[0].location, Date().dayBefore())
        }, { forecast, yesterday -> return@flatMap listOf(yesterday).plus(forecast) })
        .doOnError { view.viewState = ViewState.Error(it.message ?: "") }
        .subscribe {
          view.viewState = ViewState.Weather(ToolbarViewModel(view.context, it[0]), it)
        }
  }

  override fun onRefresh() {
    permission.checkPermission({ updateWeather() }, { onPermissionDenied(false) }, true)
  }

  fun onStop() {
    disposable.dispose()
  }

  override fun onPermissionGranted() {
    updateWeather()
  }

  override fun onPermissionDenied(userSaidNever: Boolean) {
    view.viewState = ViewState.Error(
        view.context.getString(R.string.missing_location_permission_error))
  }

}
