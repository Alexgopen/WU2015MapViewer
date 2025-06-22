package alexgopen.wurmunlimited.clientmods.wu2015map;

import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmClientMod;
import org.gotti.wurmunlimited.modsupport.console.ConsoleListener;
import org.gotti.wurmunlimited.modsupport.console.ModConsole;

public class WU2015MapViewer implements WurmClientMod, Initable, ConsoleListener {

    @Override
    public void init() {
        ModConsole.addConsoleListener(this);
    }

    @Override
    public boolean handleInput(String input, Boolean silent) {
        if (input == null) return false;

        String[] args = input.trim().split("\\s+");
        if (args.length == 0) return false;

        if (args[0].equalsIgnoreCase("wu2015map")) {
            // Launch your map viewer window in a new thread so it doesn't block the client UI thread
            new Thread(() -> ImageViewer.main(new String[0])).start();
            System.out.println("[WU2015Map] Map viewer launched.");
            return true;
        }

        return false;
    }
}