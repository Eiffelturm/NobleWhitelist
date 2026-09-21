package me.nobeld.noblewhitelist.command.admin;

import me.nobeld.noblewhitelist.NobleWhitelist;
import me.nobeld.noblewhitelist.config.ConfigData;
import me.nobeld.noblewhitelist.language.MessageData;
import me.nobeld.noblewhitelist.model.command.BaseCommand;
import me.nobeld.noblewhitelist.model.command.SubCommand;
import me.nobeld.noblewhitelist.model.whitelist.WhitelistEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.processors.confirmation.ConfirmationManager;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
import java.util.StringJoiner;
import java.util.logging.Level;
import java.util.stream.Stream;

import static me.nobeld.noblewhitelist.model.command.BaseCommand.sendMsg;
import static org.incendo.cloud.parser.standard.EnumParser.enumParser;
import static org.incendo.cloud.parser.standard.IntegerParser.integerParser;

public class WhitelistCommand {
    public enum Generator {
        DEFAULT,
        MESSAGE,
        FILE
    }
    public enum Split {
        DEFAULT,
        LINE,
        COMMA
    }

    public static List<BaseCommand> commands(NobleWhitelist plugin) {
        SubCommand list = new SubCommand(b -> b.literal("list")
                .permission("noblewhitelist.admin.list")
                .optional("page", integerParser(1))
                .handler(c -> {
                    int page = c.getOrDefault("page", 1);
                    List<WhitelistEntry> l = plugin.getStorage().listIndex(page);
                    if (l != null && !l.isEmpty()) {
                        sendMsg(c, MessageData.listPage(page));
                        l.forEach(w -> sendMsg(c, MessageData.listString(w)));
                    } else if (page > 1) sendMsg(c, MessageData.listPageEmpty(page));
                    else sendMsg(c, MessageData.whitelistEmpty());
                })
        ) {
        };

        CommandFlag<Generator> generate = CommandFlag.builder("generator").withAliases("g").withComponent(enumParser(Generator.class)).build();
        CommandFlag<Split> spliter = CommandFlag.builder("split").withAliases("s").withComponent(enumParser(Split.class)).build();
        CommandFlag<Void> rowFlag = CommandFlag.builder("row").withAliases("r").build();
        CommandFlag<Void> nameFlag = CommandFlag.builder("name").withAliases("n").build();
        CommandFlag<Void> uuidFlag = CommandFlag.builder("uuid").withAliases("u").build();

        SubCommand listAll = new SubCommand(b -> b.literal("list")
                .permission("noblewhitelist.admin.list")
                .literal("all")
                .optional("max", integerParser(-1))
                .optional("offset", integerParser(1))
                .flag(generate)
                .flag(spliter)
                .flag(rowFlag)
                .flag(nameFlag)
                .flag(uuidFlag)
                .handler(c -> {
                    int max = c.getOrDefault("max", -2);
                    long count = max;
                    int offset = max < 0 ? 1 : c.getOrDefault("offset", 1);

                    if (max == -2) {
                        count = plugin.getStorage().getTotal();
                    }

                    var generator = c.flags().getValue(generate).orElse(Generator.DEFAULT);
                    var split = c.flags().getValue(spliter).orElse(Split.DEFAULT);

                    if (count > 100 && generator == Generator.DEFAULT) {
                        sendMsg(c, MessageData.suggestFlag());
                    } else {
                        boolean rowf = c.flags().contains(rowFlag);
                        boolean namef = c.flags().contains(nameFlag);
                        boolean uuidf = c.flags().contains(uuidFlag);
                        int level = 0;
                        if (rowf) level++;
                        if (namef) level++;
                        if (uuidf) level++;
                        boolean def = level == 0;
                        boolean one = level == 1;

                        List<WhitelistEntry> l = plugin.getStorage().listAmount(max, offset);
                        if (l != null && !l.isEmpty()) {
                            if (generator == Generator.DEFAULT || generator == Generator.MESSAGE) {
                                Stream<Component> stream;
                                if (def) stream = l.stream().map(MessageData::listString);
                                else stream = l.stream().map(w -> MessageData.listString(rowf, namef, uuidf, w));

                                if (split == Split.DEFAULT) {
                                    if (one) split = Split.COMMA;
                                    else split = Split.LINE;
                                }

                                if (split == Split.COMMA) {
                                    List<Component> re = stream.toList();
                                    sendMsg(c, MessageData.listAmountSimple(re.size(), offset)
                                            .append(Component.join(JoinConfiguration.separator(Component.text(", ")), re)));
                                } else {
                                    List<Component> re = stream.filter(o -> o != null && o != Component.empty()).toList();
                                    sendMsg(c, MessageData.listAmount(re.size(), offset));
                                    re.forEach(o -> sendMsg(c, o));
                                }

                            } else {
                                var file = new File(plugin.getDataFolder().getPath(), "list.txt");
                                try (FileWriter writer = new FileWriter(file)) {
                                    if (split == Split.DEFAULT) {
                                        if (one) split = Split.COMMA;
                                        else split = Split.LINE;
                                    }
                                    StringJoiner joiner = new StringJoiner(split == Split.LINE ? "\n" : ", ");
                                    if (def) {
                                        for (WhitelistEntry entry : l) {
                                            joiner.add(MessageData.listPlainString(entry));
                                        }   
                                    } else {
                                        for (WhitelistEntry entry : l) {
                                            String re = MessageData.listPlainString(rowf, namef, uuidf, entry);
                                            if (!re.isEmpty()) joiner.add(re);
                                        }
                                    }
                                    writer.write(joiner.toString());
                                    sendMsg(c, MessageData.listWriteSuccess());
                                } catch (Exception e) {
                                    plugin.logger().log(Level.WARNING, "Exception while writing to file.", e);
                                    sendMsg(c, MessageData.listWriteError());
                                }
                            }
                        } else if (offset > 1) sendMsg(c, MessageData.listEmpty(max, offset));
                        else sendMsg(c, MessageData.whitelistEmpty());
                    }
                })
        ) {
        };
        SubCommand clearList = new SubCommand(b -> b.literal("clearlist")
                .permission("noblewhitelist.admin.list.clear")
                .meta(ConfirmationManager.META_CONFIRMATION_REQUIRED, true)
                .handler(c -> {
                    if (!plugin.getStorage().clear()) sendMsg(c, MessageData.whitelistAlreadyEmpty());
                    else sendMsg(c, MessageData.whitelistCleared());
                })
        ) {
        };
        SubCommand reload = new SubCommand(b -> b.literal("reload")
                .permission("noblewhitelist.admin.reload")
                .handler(c -> {
                    if (plugin.getStorageType().isDatabase())
                        plugin.reloadDataBase();
                    else plugin.getStorage().reload();
                    plugin.getConfigD().reloadConfig();
                    sendMsg(c, MessageData.reload());
                })
        ) {
        };
        SubCommand status = new SubCommand(b -> b.literal("status")
                .permission("noblewhitelist.admin.status")
                .handler(c -> {
                    sendMsg(c, MessageData.statusHeader());
                    sendMsg(c, MessageData.statusVersion(plugin.version()));
                    sendMsg(c, MessageData.statusWhitelistSize(plugin.getStorage().getTotal()));
                    sendMsg(c, MessageData.statusWhitelistActive(plugin.getConfigD().get(ConfigData.WhitelistCF.whitelistActive)));
                    sendMsg(c, MessageData.statusNameCheck(plugin.getConfigD().checkName()));
                    sendMsg(c, MessageData.statusUuidCheck(plugin.getConfigD().checkUUID()));
                    sendMsg(c, MessageData.statusPermCheck(plugin.getConfigD().checkPerm()));
                    sendMsg(c, MessageData.statusStorageType(plugin.getStorageType()));
                })
        ) {
        };
        SubCommand support = new SubCommand(b -> b.literal("support")
                .permission("noblewhitelist.admin.support")
                .handler(c -> plugin.getUptChecker().sendSupport(c.sender()))
        ) {
        };
        return List.of(list, listAll, clearList, reload, status, support);
    }
}
