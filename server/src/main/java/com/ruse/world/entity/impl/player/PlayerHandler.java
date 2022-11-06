package com.ruse.world.entity.impl.player;

import com.ruse.model.*;
import org.mindrot.jbcrypt.BCrypt;

import com.ruse.GameServer;
import com.ruse.GameSettings;
import com.ruse.engine.task.TaskManager;
import com.ruse.engine.task.impl.BonusExperienceTask;
import com.ruse.engine.task.impl.CombatSkullEffect;
import com.ruse.engine.task.impl.FireImmunityTask;
import com.ruse.engine.task.impl.OverloadPotionTask;
import com.ruse.engine.task.impl.PlayerRegenConstitutionTask;
import com.ruse.engine.task.impl.PlayerSkillsTask;
import com.ruse.engine.task.impl.PlayerSpecialAmountTask;
import com.ruse.engine.task.impl.PrayerRenewalPotionTask;
import com.ruse.engine.task.impl.StaffOfLightSpecialAttackTask;
import com.ruse.engine.task.impl.SummoningRegenPlayerConstitutionTask;
import com.ruse.model.Locations.Location;
import com.ruse.model.container.impl.Bank;
import com.ruse.model.container.impl.Equipment;
import com.ruse.model.definitions.WeaponAnimations;
import com.ruse.model.definitions.WeaponInterfaces;
import com.ruse.model.GameMode;
import com.ruse.net.PlayerSession;
import com.ruse.net.SessionState;
import com.ruse.net.security.ConnectionHandler;
import com.ruse.util.Misc;
import com.ruse.world.World;
import com.ruse.world.content.Achievements;
import com.ruse.world.content.BonusManager;
import com.ruse.world.content.Lottery;
import com.ruse.world.content.PlayerLogs;
import com.ruse.world.content.PlayerPanel;
import com.ruse.world.content.PlayersOnlineInterface;
import com.ruse.world.content.WellOfGoodwill;
import com.ruse.world.content.Wildywyrm;
import com.ruse.world.content.clan.ClanChatManager;
import com.ruse.world.content.combat.effect.CombatPoisonEffect;
import com.ruse.world.content.combat.effect.CombatTeleblockEffect;
import com.ruse.world.content.combat.magic.Autocasting;
import com.ruse.world.content.combat.prayer.CurseHandler;
import com.ruse.world.content.combat.prayer.PrayerHandler;
import com.ruse.world.content.combat.pvp.BountyHunter;
import com.ruse.world.content.combat.range.DwarfMultiCannon;
import com.ruse.world.content.combat.weapon.CombatSpecial;
import com.ruse.world.content.dialogue.DialogueManager;
import com.ruse.world.content.minigames.impl.Barrows;
import com.ruse.world.content.skill.impl.hunter.Hunter;
import com.ruse.world.content.skill.impl.slayer.Slayer;
import com.ruse.world.entity.impl.GlobalItemSpawner;
import com.ruse.GameSettings;
import com.ruse.engine.task.Task;
import com.ruse.engine.task.TaskManager;
import com.ruse.model.Direction;
import com.ruse.model.GameMode;
import com.ruse.model.Position;
import com.ruse.net.security.ConnectionHandler;
import com.ruse.util.Misc;
import com.ruse.world.World;
import com.ruse.world.content.dialogue.Dialogue;
import com.ruse.world.content.dialogue.DialogueExpression;
import com.ruse.world.content.dialogue.DialogueType;
import com.ruse.world.entity.impl.player.Player;


public class PlayerHandler {

	public static void handleLogin(Player player) {
		//Register the player
		System.out.println("[World] Registering player - [username, host, serial] : [" + player.getUsername() + ", " + player.getHostAddress() + ", " +player.getSerialNumber() + " ]");
		ConnectionHandler.add(player.getHostAddress());
		World.getPlayers().add(player);
		World.updatePlayersOnline();
		PlayersOnlineInterface.add(player);
		player.getSession().setState(SessionState.LOGGED_IN);

		//Packets
		player.getPacketSender().sendMapRegion().sendDetails();

		player.getRecordedLogin().reset();


		//Tabs
		player.getPacketSender().sendTabs();

		//Setting up the player's item containers..
		for(int i = 0; i < player.getBanks().length; i++) {
			if(player.getBank(i) == null) {
				player.setBank(i, new Bank(player));
			}
		}
		player.getInventory().refreshItems();
		player.getEquipment().refreshItems();

		//Weapons and equipment..
		WeaponAnimations.update(player);
		WeaponInterfaces.assign(player, player.getEquipment().get(Equipment.WEAPON_SLOT));
		CombatSpecial.updateBar(player);
		BonusManager.update(player);

		//Skills
		player.getSummoning().login();
		player.getFarming().load();
		Slayer.checkDuoSlayer(player, true);
		for (Skill skill : Skill.values()) {
			player.getSkillManager().updateSkill(skill);
		}

		//Relations
		player.getRelations().setPrivateMessageId(1).onLogin(player).updateLists(true);

		//Client configurations
		player.getPacketSender().sendConfig(172, player.isAutoRetaliate() ? 1 : 0)
		.sendTotalXp(player.getSkillManager().getTotalGainedExp())
		.sendConfig(player.getFightType().getParentId(), player.getFightType().getChildId())
		.sendRunStatus().sendRunEnergy(player.getRunEnergy()).sendRights()
		.sendString(8135, ""+player.getMoneyInPouch())
		.sendInteractionOption("Follow", 3, false)
		.sendInteractionOption("Trade With", 4, false);
		//.sendInterfaceRemoval().sendString(39161, "@or2@Server time: @or2@[ @yel@"+Misc.getCurrentServerTime()+"@or2@ ]");

		Autocasting.onLogin(player);
		PrayerHandler.deactivateAll(player);
		CurseHandler.deactivateAll(player);
		BonusManager.sendCurseBonuses(player);
		Achievements.updateInterface(player);
		Barrows.updateInterface(player);

		//Tasks
		TaskManager.submit(new PlayerSkillsTask(player));
		TaskManager.submit(new PlayerRegenConstitutionTask(player));
		TaskManager.submit(new SummoningRegenPlayerConstitutionTask(player));
		if (player.isPoisoned()) {
			TaskManager.submit(new CombatPoisonEffect(player));
		}
		if(player.getPrayerRenewalPotionTimer() > 0) {
			TaskManager.submit(new PrayerRenewalPotionTask(player));
		}
		if(player.getOverloadPotionTimer() > 0) {
			TaskManager.submit(new OverloadPotionTask(player));
		}
		if (player.getTeleblockTimer() > 0) {
			TaskManager.submit(new CombatTeleblockEffect(player));
		}
		if (player.getSkullTimer() > 0) {
			player.setSkullIcon(1);
			TaskManager.submit(new CombatSkullEffect(player));
		}
		if(player.getFireImmunity() > 0) {
			FireImmunityTask.makeImmune(player, player.getFireImmunity(), player.getFireDamageModifier());
		}
		if(player.getSpecialPercentage() < 100) {
			TaskManager.submit(new PlayerSpecialAmountTask(player));
		}
		if(player.hasStaffOfLightEffect()) {
			TaskManager.submit(new StaffOfLightSpecialAttackTask(player));
		}
		if(player.getMinutesBonusExp() >= 0) {
			TaskManager.submit(new BonusExperienceTask(player));
		}

		//Update appearancez

		player.lockNFTs(player);

		//Others
		Lottery.onLogin(player);
		Locations.login(player);
		player.getPacketSender().sendMessage("@bla@Welcome to "+GameSettings.RSPS_NAME+"!");
		System.out.println("wallet "+ player.getWallet());
		/*if (!player.newPlayer()) {
			if (player.getWallet() == "none" || player.getWallet() == null || player.getWallet() == "null") {
				player.getPacketSender().sendMessage("Please set your Fuel wallet by typing ::wallet followed by your Fuel address");
				player.getPacketSender().sendMessage("For example '::wallet MY_FUEL_ADDRESS_HERE'");
			}
		}*/
		if(player.experienceLocked())
			player.getPacketSender().sendMessage(MessageType.SERVER_ALERT, " @red@Warning: your experience is currently locked.");
		
		if (!player.getRights().OwnerDeveloperOnly() && player.getSkillManager().getExperience(Skill.CONSTRUCTION) > 1) {
			player.getSkillManager().setExperience(Skill.CONSTRUCTION, 0);
			player.getSkillManager().setMaxLevel(Skill.CONSTRUCTION, 1);
			player.getSkillManager().setCurrentLevel(Skill.CONSTRUCTION, 1, true);
		}
		
		
		if (GameSettings.BCRYPT_HASH_PASSWORDS && Misc.needsNewSalt(player.getSalt())) {
			player.setSalt(BCrypt.gensalt(GameSettings.BCRYPT_ROUNDS));
			System.out.println(player.getUsername()+" needs a new salt. Generated one, rounds ("+GameSettings.BCRYPT_ROUNDS+")");
		}

		
		if (Wildywyrm.wyrmAlive) {
			Wildywyrm.sendHint(player);
		}
		
		if(WellOfGoodwill.isActive()) {
			player.getPacketSender().sendMessage(MessageType.SERVER_ALERT, "The Well of Goodwill is granting 30% bonus experience for another "+WellOfGoodwill.getMinutesRemaining()+" minutes.");
		}

		PlayerPanel.refreshPanel(player);


		//New player
		if(player.newPlayer()) {
			//player.setClanChatName("FuelScape");
			player.setNewPlayer(false);
			player.getInventory().add(995, 1000000).add(1153, 1).add(1115, 1).add(1067, 1).add(1323, 1).add(1191, 1).add(841, 1).add(882, 1000).add(1167, 1).add(1129, 1).add(1095, 1).add(1063, 1).add(1379, 1).add(556, 1000).add(558, 1000).add(557, 1000).add(554, 1000).add(555, 1000).add(1351, 1).add(1265, 1).add(1712, 1).add(11118, 1).add(1007, 1).add(386, 100).add(9003, 1);
			player.setReceivedStarter(true);
			player.getPacketSender().sendMessage("You claim your normal starter kit!");
			ConnectionHandler.addStarter(player.getHostAddress(), true);
			World.sendMessage("<shad=1><col=F5FF3B><img=10> "+player.getUsername()+" has just logged in for the first time!");
			player.getPacketSender().sendInterface(3559);
			player.getAppearance().setCanChangeAppearance(true);
			player.setPlayerLocked(false);
			player.getPacketSender().sendMessage("Please set your Fuel wallet typing your username into the website form");
			//player.getPacketSender().sendMessage("Please set your Fuel wallet by typing ::wallet followed by your Fuel address");
			//player.getPacketSender().sendMessage("For example '::wallet MY_FUEL_ADDRESS_HERE'");
		}
		
		ClanChatManager.handleLogin(player);
		
		player.getPacketSender().updateSpecialAttackOrb().sendIronmanMode(player.getGameMode().ordinal());

		if(player.getRights() == PlayerRights.SUPPORT)
			World.sendMessage(MessageType.PLAYER_ALERT, ("<shad=0><col="+player.getYellHex()+"> Support Member "+player.getUsername()+" has just logged in, feel free to message them for help!"));
		if(player.getRights() == PlayerRights.MODERATOR)
			World.sendMessage(MessageType.PLAYER_ALERT, ("<shad=0><col="+player.getYellHex()+"> Moderator "+player.getUsername()+" has just logged in."));
		if(player.getRights() == PlayerRights.ADMINISTRATOR)
			World.sendMessage(MessageType.PLAYER_ALERT, ("<shad=0><col="+player.getYellHex()+"> Administrator "+player.getUsername()+" has just logged in."));
		if(player.getRights() ==PlayerRights.DEVELOPER)
			World.sendMessage(MessageType.PLAYER_ALERT, ("<shad=0><col="+player.getYellHex()+"> Developer "+player.getUsername()+" has just logged in."));
		if(player.getRights() ==PlayerRights.OWNER)
			World.sendMessage(MessageType.PLAYER_ALERT, ("<shad=0><col="+player.getYellHex()+">Owner "+player.getUsername()+" has just logged in."));
		
		//GrandExchange.onLogin(player);
		
		if(player.getPointsHandler().getAchievementPoints() == 0) {
			Achievements.setPoints(player);
		}
		
		player.getUpdateFlag().flag(Flag.APPEARANCE);
		PlayerLogs.log(player.getUsername(), "Login. ip: "+player.getHostAddress()+", serial #: "+player.getSerialNumber());
		/* if(player.getSkillManager().getCurrentLevel(Skill.CONSTITUTION) == 0){
			player.getSkillManager().setCurrentLevel(Skill.CONSTITUTION, 1);
			World.deregister(player);
			System.out.println(player.getUsername()+" logged in from a bad session. They have 0 HP and are nulled. Set them to 1 and kicked them.");
			// TODO this may cause dupes. removed temp.
		} */
		if (player.isInDung()) {
			System.out.println(player.getUsername()+" logged in from a bad dungeoneering session.");
			PlayerLogs.log(player.getUsername(), " logged in from a bad dungeoneering session. Inv/equipment wiped.");
			player.getInventory().resetItems().refreshItems();
			player.getEquipment().resetItems().refreshItems();
			if (player.getLocation() == Location.DUNGEONEERING) {
				player.moveTo(GameSettings.DEFAULT_POSITION.copy());
			}
			player.getPacketSender().sendMessage("Your Dungeon has been disbanded.");
			player.setInDung(false);
		}
		if (player.getLocation() == Location.GRAVEYARD && player.getPosition().getY() > 3566) {
			PlayerLogs.log(player.getUsername(), "logged in inside the graveyard arena, moved their ass out.");
			player.moveTo(new Position(3503, 3565, 0));
			player.setPositionToFace(new Position(3503, 3566));
			player.getPacketSender().sendMessage("You logged off inside the graveyard arena. Moved you to lobby area.");
		}
		if (player.getPosition().getX() == 3004 && player.getPosition().getY() >= 3938 && player.getPosition().getY() <= 3949) {
			PlayerLogs.log(player.getUsername(), player.getUsername()+" was stuck in the obstacle pipe in the Wild.");
			player.moveTo(new Position(3006, player.getPosition().getY(), player.getPosition().getZ()));
			player.getPacketSender().sendMessage("You logged off inside the obstacle pipe, moved out.");
		}
		GlobalItemSpawner.spawnGlobalGroundItems(player);
		player.unlockPkTitles();
		//player.getPacketSender().sendString(39160, "@or2@Players online:   @or2@[ @yel@"+(int)(World.getPlayers().size())+"@or2@ ]"); Handled by PlayerPanel.java
		player.getPacketSender().sendString(57003, "Players:  @gre@"+(int)(World.getPlayers().size()));
		
	}

	public static boolean handleLogout(Player player, Boolean forced) {
		try {

			PlayerSession session = player.getSession();
			
			if(session.getChannel().isOpen()) {
				session.getChannel().close();
			}

			if(!player.isRegistered()) {
				return true;
			}

			boolean exception = forced || GameServer.isUpdating() || World.getLogoutQueue().contains(player) && player.getLogoutTimer().elapsed(90000);
			if(player.logout() || exception) {
				//new Thread(new HighscoresHandler(player)).start();
				System.out.println("[World] Deregistering player - [username, host] : [" + player.getUsername() + ", " + player.getHostAddress() + "]");
				player.getSession().setState(SessionState.LOGGING_OUT);
				ConnectionHandler.remove(player.getHostAddress());
				player.setTotalPlayTime(player.getTotalPlayTime() + player.getRecordedLogin().elapsed());
				player.getPacketSender().sendInterfaceRemoval();
				if(player.getCannon() != null) {
					DwarfMultiCannon.pickupCannon(player, player.getCannon(), true);
				}
				if(exception && player.getResetPosition() != null) {
					player.moveTo(player.getResetPosition());
					player.setResetPosition(null);
				}
				if(player.getRegionInstance() != null) {
					player.getRegionInstance().destruct();
				}
				Hunter.handleLogout(player);
				Locations.logout(player);
				player.getSummoning().unsummon(false, false);
				player.getFarming().save();
				BountyHunter.handleLogout(player);
				ClanChatManager.leave(player, false);
				player.getRelations().updateLists(false);
				PlayersOnlineInterface.remove(player);
				TaskManager.cancelTasks(player.getCombatBuilder());
				TaskManager.cancelTasks(player);
				player.save();
				World.getPlayers().remove(player);
				session.setState(SessionState.LOGGED_OUT);
				World.updatePlayersOnline();
				return true;
			} else {
				return false;
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
		return true;
	}
}
