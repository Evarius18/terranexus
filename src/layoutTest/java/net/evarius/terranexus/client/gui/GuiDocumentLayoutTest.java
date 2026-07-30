package net.evarius.terranexus.client.gui;

import net.evarius.terranexus.network.gui.GuiMenuElement;
import net.minecraft.text.Text;

import java.util.List;

public final class GuiDocumentLayoutTest {
    private GuiDocumentLayoutTest() {}

    public static void run() {
        require(GuiPageType.detect("Grundbuchauszug · Eigentum") == GuiPageType.DOCUMENT_LIST,
                "Grundbuchauszug wurde nicht als Dokumentliste erkannt");
        require(GuiPageType.detect("Grundstücksakte") == GuiPageType.DOCUMENT_DETAIL,
                "Grundstücksakte wurde nicht als Dokumentdetail erkannt");
        require(GuiPageType.detect("TerraNexus Bürgerinformation") == GuiPageType.IDENTITY_CARD,
                "Personalausweis wurde nicht als eigene Kartenansicht erkannt");
        require(GuiPageType.detect("TerraNexus Shop") == GuiPageType.SHOP_DIALOG,
                "Shop wurde nicht als kompakter Dialog erkannt");
        require(GuiPageType.detect("Grundbuch · Neue Umschreibung") == GuiPageType.LIST,
                "Umschreibungsablauf wurde nicht als Verwaltungsseite erkannt");

        GuiPageModel list = GuiPageModel.create("Grundbuchauszug · Eigentum", List.of(
                element(4, false, "Grundbuchauszug"),
                element(1, true, "Grundstücksmarkt"),
                element(9, true, "Mustergrundstück")));
        require(list.elements(GuiElementRole.HEADER).size() == 1, "Dokumentkopf fehlt");
        require(list.elements(GuiElementRole.TOOLBAR).size() == 1, "Dokumentwerkzeugleiste fehlerhaft");
        require(list.elements(GuiElementRole.LIST_ROW).size() == 1, "Grundstückszeile fehlt");

        GuiPageModel detail = GuiPageModel.create("Grundstücksakte", List.of(
                element(4, false, "Mustergrundstück"),
                element(20, false, "Lage"),
                element(22, true, "Markierung"),
                element(53, true, "Akte schließen")));
        require(detail.elements(GuiElementRole.DETAIL_INFO).size() == 1, "Akteninformation fehlt");
        require(detail.elements(GuiElementRole.ACTION).size() == 1, "Aktenaktion fehlt");
        require(detail.elements(GuiElementRole.NAVIGATION).size() == 1, "Aktenabschluss fehlt");

        GuiPageModel transfer = GuiPageModel.create("Grundbuch · Neue Umschreibung", List.of(
                element(4, false, "Grundstück auswählen"),
                element(6, true, "Neue Umschreibung"),
                element(9, true, "Mustergrundstück")));
        require(transfer.elements(GuiElementRole.TOOLBAR).size() == 1,
                "Umschreibungsaktion fehlt in der Toolbar");
        require(transfer.elements(GuiElementRole.LIST_ROW).size() == 1,
                "Grundstücksauswahl fehlt in der Umschreibungsliste");
    }

    private static GuiMenuElement element(int id, boolean enabled, String label) {
        return new GuiMenuElement(id, "DOCUMENT", Text.literal(label), Text.empty(), enabled, false);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
