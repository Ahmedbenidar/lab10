-- =====================================================================
-- Migration version 2.0
--   * ajout de la colonne "description" a la table "salles"
--   * unicite de l'email des utilisateurs
--   * index sur les plages horaires pour accelerer la detection de conflit
-- =====================================================================

ALTER TABLE salles ADD COLUMN description VARCHAR(255);

CREATE UNIQUE INDEX idx_utilisateur_email ON utilisateurs (email);

CREATE INDEX idx_reservation_salle_dates ON reservations (salle_id, date_debut);
