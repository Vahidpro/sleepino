package pasac.sleepino.platform
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import pasac.sleepino.MainActivity
class BedtimeTile: TileService() {
 override fun onStartListening(){super.onStartListening();qsTile?.apply{state=Tile.STATE_ACTIVE;updateTile()}}
 // The Intent overload is the only collapse API on Android 8–13; the Android 14+ path uses PendingIntent.
 @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
 override fun onClick(){super.onClick();val intent=Intent(this,MainActivity::class.java).setAction(MainActivity.ACTION_SLEEP).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
  if(Build.VERSION.SDK_INT>=34)startActivityAndCollapse(PendingIntent.getActivity(this,4,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
  else {@Suppress("DEPRECATION") startActivityAndCollapse(intent)}
 }
}
