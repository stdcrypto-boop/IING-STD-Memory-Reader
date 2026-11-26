package id.std.memoryreader;
import android.app.*;import android.os.*;import android.hardware.usb.*;import android.content.*;import android.widget.*;
public class MainActivity extends Activity {
 TextView status; UsbManager manager;
 public void onCreate(Bundle b){super.onCreate(b);manager=(UsbManager)getSystemService(USB_SERVICE);LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(32,64,32,32);TextView title=new TextView(this);title.setText("IING STD Memory Reader");title.setTextSize(26);l.addView(title);status=new TextView(this);status.setTextSize(17);l.addView(status);Button scan=new Button(this);scan.setText("Deteksi card reader USB");l.addView(scan);scan.setOnClickListener(v->scan());setContentView(l);scan();}
 void scan(){StringBuilder s=new StringBuilder("Mode deteksi saja — tidak menulis ke kartu.\n\n");int n=0;for(UsbDevice d:manager.getDeviceList().values()){for(int i=0;i<d.getInterfaceCount();i++){UsbInterface f=d.getInterface(i);if(f.getInterfaceClass()==8){n++;s.append("USB ").append(d.getVendorId()).append(":").append(d.getProductId()).append("\n");s.append(f.getInterfaceSubclass()==6&&f.getInterfaceProtocol()==80?"SCSI BOT terdeteksi":"Protokol belum didukung").append("\n");}}}if(n==0)s.append("Card reader belum terdeteksi.");status.setText(s.toString());}
}
