package me.herex.karmsmp.storage;

import java.util.Optional;
import java.util.UUID;

public interface RankStorage {

    void connect() throws Exception;

    void close();

    Optional<String> getPlayerRank(UUID uuid);

    void setPlayerRank(UUID uuid, String username, String rankName);

    void removePlayerRank(UUID uuid);

    String getStorageName();
}
