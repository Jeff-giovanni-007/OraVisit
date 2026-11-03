export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api/v1',
  // décalage du fuseau du backend (APP_TIMEZONE = Africa/Lome, UTC+0 toute l'année) : les heures affichées
  // sont celles des exports Excel et des e-mails, quel que soit le fuseau du poste
  timezone: '+0000',
};
