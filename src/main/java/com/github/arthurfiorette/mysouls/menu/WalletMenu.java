package com.github.arthurfiorette.mysouls.menu;

import com.github.arthurfiorette.mysouls.config.Config;
import com.github.arthurfiorette.mysouls.config.ConfigFile;
import com.github.arthurfiorette.mysouls.lang.Lang;
import com.github.arthurfiorette.mysouls.lang.LangFile;
import com.github.arthurfiorette.mysouls.listeners.ChatListener;
import com.github.arthurfiorette.mysouls.model.Wallet;
import com.github.arthurfiorette.mysouls.model.WalletUtils;
import com.github.arthurfiorette.mysouls.storage.WalletStorage;
import com.github.arthurfiorette.sinklibrary.executor.TaskContext;
import com.github.arthurfiorette.sinklibrary.interfaces.BasePlugin;
import com.github.arthurfiorette.sinklibrary.item.ItemBuilder;
import com.github.arthurfiorette.sinklibrary.item.ItemBuilders;
import com.github.arthurfiorette.sinklibrary.item.ItemProperty;
import com.github.arthurfiorette.sinklibrary.menu.PageableMenu;
import com.github.arthurfiorette.sinklibrary.menu.item.BuilderStack;
import com.github.arthurfiorette.sinklibrary.menu.item.MenuItem;
import com.github.arthurfiorette.sinklibrary.replacer.ReplacerFunction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class WalletMenu extends PageableMenu {

  public static ItemStack empty = new ItemBuilder(Material.STAINED_GLASS_PANE)
    .setItemFlags()
    .setName(" ")
    .remove(ItemProperty.LORE)
    .build();

  private static final MenuItem mhfQuestion = ItemBuilders.ofHead("MHF_Question").asMenuItem();

  /**
   * Last pageable items build
   */
  private List<MenuItem> items;

  /**
   * The souls hashcode to prevent double rendering
   *
   * @see Wallet#getSouls()
   */
  private int soulsHashcode;
  private ReplacerFunction replacer;

  private final LangFile lang;
  private final ConfigFile config;
  private final ChatListener chatListener;
  private final WalletStorage walletStorage;

  /**
   * @throws NoSuchElementException if this wallet owner isn't online
   */
  public WalletMenu(final BasePlugin plugin, final Player player) throws NoSuchElementException {
    super(plugin, player, "Almas de " + player.getName(), 6);
    super.setDefaultItem(WalletMenu.empty);

    this.lang = plugin.getComponent(LangFile.class);
    this.config = plugin.getComponent(ConfigFile.class);
    this.chatListener = plugin.getComponent(ChatListener.class);
    this.walletStorage = plugin.getComponent(WalletStorage.class);
  }

  @Override
  public void update() {
    super.update();
    this.updateReplacer();
  }

  @Override
  protected byte[] pageableSlots() {
    return new byte[] {
      12,
      13,
      14,
      15,
      16,
      21,
      22,
      23,
      24,
      25,
      30,
      31,
      32,
      33,
      34,
      35,
      36,
      37,
      38,
      39,
    };
  }

  @Override
  protected List<MenuItem> pageableItems() {
    final Map<UUID, Integer> souls = this.getWallet().getSouls();

    if (souls.hashCode() == this.soulsHashcode) {
      return this.items;
    }

    this.soulsHashcode = souls.hashCode();
    final List<MenuItem> items = souls
      .entrySet()
      .stream()
      .map(
        entry -> {
          final OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
          final ReplacerFunction replacer = this.replacer.add("{player}", player.getName());

          final ItemBuilder builder = ItemBuilders
            .ofHead(player)
            .setAmount(entry.getValue())
            .setName(this.lang.getString(Lang.INVENTORY_SOUL_LORE, replacer))
            .setLore(this.lang.getStringList(Lang.INVENTORY_SOUL_LORE, replacer));

          return new BuilderStack(builder);
        }
      )
      .collect(Collectors.toList());

    return items;
  }

  @Override
  @SuppressWarnings("deprecation")
  protected Map<Byte, MenuItem> staticItems() {
    final Map<Byte, MenuItem> map = new HashMap<>();

    map.put(
      (byte) 10,
      ItemBuilders
        .ofHead(this.getOwner())
        .setName(this.lang.getString(Lang.YOUR_WALLET_NAME, this.replacer))
        .setLore(this.lang.getStringList(Lang.YOUR_WALLET_LORE, this.replacer))
        .asMenuItem()
    );

    map.put(
      (byte) 19,
      ItemBuilders
        .ofHeadUrl(this.config.getString(Config.TROPHY_HEAD_URL))
        .setName(this.lang.getString(Lang.RANKING_NAME, this.replacer))
        .setLore(this.lang.getStringList(Lang.RANKING_LORE, this.replacer))
        .asMenuItem()
    );

    map.put(
      (byte) 26,
      !this.hasPreviousPage()
        ? null
        : new ItemBuilder(Material.STONE_BUTTON)
          .setItemFlags()
          .setName(this.lang.getString(Lang.BACKWARD, this.replacer))
          .asMenuItem(
            (item, action) -> {
              this.previousPage(true);
            }
          )
    );

    map.put(
      (byte) 35,
      !this.hasNextPage()
        ? null
        : new ItemBuilder(Material.STONE_BUTTON)
          .setName(this.lang.getString(Lang.FORWARD, this.replacer))
          .asMenuItem(
            (item, action) -> {
              this.nextPage(true);
            }
          )
    );

    map.put((byte) 45, WalletMenu.mhfQuestion);

    map.put(
      (byte) 49,
      ItemBuilders
        .ofHeadUrl(this.config.getString(Config.SOUL_HEAD_URL))
        .setName(this.lang.getString(Lang.WITHDRAW_SOULS_NAME, this.replacer))
        .setLore(this.lang.getStringList(Lang.WITHDRAW_SOULS_LORE, this.replacer))
        .asMenuItem(
          (item, action) -> {
            TaskContext.SYNC.run(this.basePlugin, () -> this.owner.closeInventory());

            // Send the question message
            this.owner.sendMessage(this.lang.getString(Lang.SOUL_CHAT_MESSAGE));

            // Add a chat action
            this.chatListener.addAction(
                this.owner,
                message -> {
                  final String[] args = message.split(" ");
                  final Wallet wallet = this.walletStorage.getSync(this.owner);

                  UUID soul;

                  // If the argument is *, get the soul with the biggest quantity.
                  if (args[0].equalsIgnoreCase("*")) {
                    final Entry<UUID, Integer> entry = WalletUtils.biggestEntry(wallet);

                    // If entry is null, the player does not have any souls.
                    if (entry == null) {
                      this.owner.sendMessage(this.lang.getString(Lang.DONT_HAVE_SOULS));
                      return;
                    }

                    soul = entry.getKey();
                  } else {
                    soul = Bukkit.getOfflinePlayer(args[0]).getUniqueId();
                  }

                  final Lang response = WalletUtils.withdrawSoul(wallet, soul);
                  this.owner.sendMessage(this.lang.getString(response));
                }
              );
          }
        )
    );

    map.put(
      (byte) 51,
      ItemBuilders
        .ofHeadUrl(this.config.getString(Config.COIN_HEAD_URL))
        .setName(this.lang.getString(Lang.WITHDRAW_COINS_NAME, this.replacer))
        .setLore(this.lang.getStringList(Lang.WITHDRAW_COINS_LORE, this.replacer))
        .asMenuItem(
          (item, action) -> {
            TaskContext.SYNC.run(this.basePlugin, () -> this.owner.closeInventory());
            this.owner.sendMessage(this.lang.getString(Lang.COIN_CHAT_MESSAGE));

            this.chatListener.addAction(
                this.owner,
                message -> {
                  final String[] args = message.split(" ");
                  final Wallet wallet = this.walletStorage.getSync(this.owner);

                  int amount;
                  try {
                    amount = Integer.parseInt(args[0]);
                  } catch (final Exception e) {
                    this.owner.sendMessage(
                        this.lang.getString(Lang.NOT_A_NUMBER, r -> r.add("{text}", args[0]))
                      );
                    return;
                  }

                  final Lang response = WalletUtils.withdrawCoins(wallet, amount);
                  this.owner.sendMessage(this.lang.getString(response));
                }
              );
          }
        )
    );

    map.put(
      (byte) 53,
      this.getPage() <= 0
        ? null
        : new ItemBuilder(Material.PAPER)
          .setItemFlags()
          .setName(this.lang.getString(Lang.PAGE, this.replacer))
          .setAmount(this.getPage() + 1)
          .asMenuItem()
    );

    return map;
  }

  private Wallet getWallet() {
    return this.basePlugin.getComponent(WalletStorage.class).getSync(this.owner.getUniqueId());
  }

  private void updateReplacer() {
    final Entry<UUID, Integer> biggestEntry = WalletUtils.biggestEntry(this.getWallet());
    this.replacer =
      r -> {
        r.add("{souls}", this.getWallet().getSoulCount() + "");
        r.add("{players}", this.getWallet().getPlayerCount() + "");
        r.add("{average}", WalletUtils.soulsRatio(this.getWallet()) + "");
        r.add(
          "{more-souls}",
          biggestEntry != null
            ? Bukkit.getOfflinePlayer(biggestEntry.getKey()).getName()
            : "Sem almas suficientes."
        );
        return r;
      };
  }
}
