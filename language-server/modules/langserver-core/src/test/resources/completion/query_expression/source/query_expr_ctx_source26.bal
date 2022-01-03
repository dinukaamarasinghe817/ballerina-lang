import ballerina/module1;

function findToptenCountriesByAthletes() returns error? {
    map<int> athletesByCountry = {};
    string[] listResult = from [string,int] entry in athletesByCountry.entries() 
                     where entry[1] > a
                     order by entry[1] 
                     limit 10 select entry[0];
    
    foreach string item in listResult {
    }
}

type MedalStats record {
    string country;
    int totalMedals;
};
