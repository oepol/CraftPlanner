import java.util.*;
import java.util.jar.*;
public class SmokeCheck {
  public static void main(String[] args) throws Exception {
    int count=0;
    try(JarFile jar=new JarFile(args[0])) {
      for(var entry:Collections.list(jar.entries())) {
        String name=entry.getName();
        if(name.endsWith(".class")) {
          if(!name.startsWith("dev/ole/craftplanner/")) throw new AssertionError("Unexpected bundled class "+name);
          if(args.length>1 && args[1].equals("without-modmenu") && name.contains("/compat/"))continue;
          Class.forName(name.substring(0,name.length()-6).replace('/','.'),false,SmokeCheck.class.getClassLoader());
          count++;
        }
      }
      if(jar.getEntry("fabric.mod.json")==null) throw new AssertionError("Missing Fabric metadata");
    }
    if(args.length==1) {
      var compat=Class.forName("dev.ole.craftplanner.compat.ModMenuIntegration").getDeclaredConstructor().newInstance();
      Object factory=compat.getClass().getMethod("getModConfigScreenFactory").invoke(compat);
      if(factory==null)throw new AssertionError("Missing config screen factory");
    }
    var type=Class.forName("dev.ole.craftplanner.CraftPlannerClient");
    if(!net.fabricmc.api.ClientModInitializer.class.isAssignableFrom(type)) throw new AssertionError("Wrong entrypoint type");
    Object initializer=type.getDeclaredConstructor().newInstance();
    System.out.println("PASS: "+count+" mod classes linked; Fabric client entrypoint constructed; no game/dependency classes bundled.");
    System.out.println("Not tested: Fabric event initialization, renderer, real world, network connection.");
  }
}
