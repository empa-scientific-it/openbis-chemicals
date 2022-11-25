import { useUser } from "./login";
import * as Openbis from '../api/openbis' 
const userStore = useUser()


export async function getToken(): Promise<string>{
    if (userStore.loggedIn && await  Openbis.checkToken(userStore.token)){
        return userStore.token;
    }else{
        throw new Error("Token invalid")
    }
}